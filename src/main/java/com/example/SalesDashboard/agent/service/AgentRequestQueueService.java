package com.example.SalesDashboard.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Per-Tally-Agent request queue.
 *
 * IMPORTANT:
 *
 * - Each Tally Agent has its own queue.
 * - Only ONE Tally request is processed at a time per Agent.
 * - Different Agents can process requests simultaneously.
 * - Tally business data is NOT stored here.
 *
 * Example:
 *
 * Agent-A
 *   Request 1 -> RUNNING
 *   Request 2 -> WAITING
 *   Request 3 -> WAITING
 *
 * Agent-B
 *   Request 4 -> RUNNING
 *   Request 5 -> WAITING
 */
@Slf4j
@Service
public class AgentRequestQueueService {

    /**
     * Maximum number of requests waiting in the queue
     * for a single Agent, excluding the currently running request.
     *
     * Example:
     *
     * 1 request running
     * + 20 requests waiting
     */
    private static final int MAX_QUEUE_SIZE = 20;

    /**
     * One executor per Tally Agent.
     */
    private final ConcurrentHashMap<String, ThreadPoolExecutor> agentExecutors =
            new ConcurrentHashMap<>();

    /**
     * Submit a request to the queue of a specific Agent.
     */
    public <T> CompletableFuture<T> submit(
            String agentId,
            String requestDescription,
            Supplier<T> task
    ) {

        if (agentId == null || agentId.isBlank()) {

            CompletableFuture<T> failed =
                    new CompletableFuture<>();

            failed.completeExceptionally(
                    new IllegalArgumentException(
                            "Agent ID is required for queue submission"
                    )
            );

            return failed;
        }

        ThreadPoolExecutor executor =
                getOrCreateExecutor(agentId);

        int queueSizeBeforeSubmit =
                executor.getQueue().size();

        int activeBeforeSubmit =
                executor.getActiveCount();

        log.info(
                "TALLY QUEUE SUBMIT | agentId={} | description={} | active={} | waiting={}",
                agentId,
                requestDescription,
                activeBeforeSubmit,
                queueSizeBeforeSubmit
        );

        try {

            return CompletableFuture.supplyAsync(
                    () -> {

                        log.info(
                                "TALLY QUEUE START | agentId={} | description={} | active={} | waiting={}",
                                agentId,
                                requestDescription,
                                executor.getActiveCount(),
                                executor.getQueue().size()
                        );

                        try {

                            return task.get();

                        } finally {

                            log.info(
                                    "TALLY QUEUE END | agentId={} | description={} | active={} | waiting={}",
                                    agentId,
                                    requestDescription,
                                    Math.max(
                                            executor.getActiveCount() - 1,
                                            0
                                    ),
                                    executor.getQueue().size()
                            );
                        }
                    },
                    executor
            );

        } catch (java.util.concurrent.RejectedExecutionException e) {

            log.warn(
                    "TALLY QUEUE FULL | agentId={} | description={} | waiting={}",
                    agentId,
                    requestDescription,
                    executor.getQueue().size()
            );

            CompletableFuture<T> failed =
                    new CompletableFuture<>();

            failed.completeExceptionally(
                    new AgentQueueFullException(
                            "Tally Agent is currently busy. "
                                    + "Too many requests are waiting for this Agent. "
                                    + "Please wait and try again."
                    )
            );

            return failed;
        }
    }

    /**
     * Returns current number of requests for an Agent.
     *
     * Includes:
     *
     * - currently running request
     * - waiting requests
     *
     * This is useful for automatic load-aware agent selection.
     */
    public int getLoad(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            return Integer.MAX_VALUE;
        }

        ThreadPoolExecutor executor =
                agentExecutors.get(agentId);

        if (executor == null) {
            return 0;
        }

        return executor.getActiveCount()
                + executor.getQueue().size();
    }

    /**
     * Number of waiting requests for an Agent.
     */
    public int getWaitingCount(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            return 0;
        }

        ThreadPoolExecutor executor =
                agentExecutors.get(agentId);

        if (executor == null) {
            return 0;
        }

        return executor.getQueue().size();
    }

    /**
     * Whether Agent currently has an active request.
     */
    public boolean isBusy(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            return false;
        }

        ThreadPoolExecutor executor =
                agentExecutors.get(agentId);

        if (executor == null) {
            return false;
        }

        return executor.getActiveCount() > 0;
    }

    /**
     * Create one single-thread executor for each Agent.
     *
     * Why one thread?
     *
     * Because one TallyPrime instance should receive
     * only one heavy Tally request at a time.
     *
     * Different Agents have different executors,
     * therefore different Agents work in parallel.
     */
    private ThreadPoolExecutor getOrCreateExecutor(
            String agentId
    ) {

        return agentExecutors.computeIfAbsent(
                agentId,
                id -> {

                    ThreadPoolExecutor executor =
                            new ThreadPoolExecutor(
                                    1,
                                    1,
                                    0L,
                                    TimeUnit.MILLISECONDS,
                                    new ArrayBlockingQueue<>(
                                            MAX_QUEUE_SIZE
                                    ),
                                    runnable -> {

                                        Thread thread =
                                                new Thread(
                                                        runnable,
                                                        "tally-agent-queue-" + id
                                                );

                                        thread.setDaemon(true);

                                        return thread;
                                    },
                                    new ThreadPoolExecutor.AbortPolicy()
                            );

                    log.info(
                            "Created Tally queue for Agent | agentId={}",
                            id
                    );

                    return executor;
                }
        );
    }
}