package com.example.SalesDashboard.subscription.service;

import com.example.SalesDashboard.subscription.dto.SubscriptionCreateRequest;
import com.example.SalesDashboard.subscription.dto.SubscriptionUpdateRequest;
import com.example.SalesDashboard.subscription.entity.Subscription;
import com.example.SalesDashboard.subscription.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /*
     * Create subscription
     */
    public Subscription createSubscription(
            SubscriptionCreateRequest request) {

        if (request.getPlanName() == null
                || request.getPlanName().isBlank()) {

            throw new IllegalArgumentException(
                    "Plan name is required"
            );
        }

        if (request.getMaxUsers() == null
                || request.getMaxUsers() <= 0) {

            throw new IllegalArgumentException(
                    "Maximum users must be greater than 0"
            );
        }

        Date now = new Date();

        Subscription subscription =
                Subscription.builder()

                        // Generate UUID automatically
                        .id(UUID.randomUUID().toString())

                        .planName(request.getPlanName())

                        .maxUsers(request.getMaxUsers())

                        .status(
                                request.getStatus() != null
                                        ? request.getStatus()
                                        : "TRIAL"
                        )

                        .createdAt(now)

                        .updatedAt(now)

                        .build();

        return subscriptionRepository.save(subscription);
    }

    /*
     * Get all subscriptions
     */
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    /*
     * Get subscription by ID
     */
    public Subscription getSubscriptionById(String id) {

        return subscriptionRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Subscription not found: " + id
                        )
                );
    }

    /*
     * Update subscription
     */
    public Subscription updateSubscription(
            String id,
            SubscriptionUpdateRequest request) {

        Subscription subscription =
                getSubscriptionById(id);

        if (request.getPlanName() != null
                && !request.getPlanName().isBlank()) {

            subscription.setPlanName(
                    request.getPlanName()
            );
        }

        if (request.getMaxUsers() != null) {

            if (request.getMaxUsers() <= 0) {

                throw new IllegalArgumentException(
                        "Maximum users must be greater than 0"
                );
            }

            subscription.setMaxUsers(
                    request.getMaxUsers()
            );
        }

        if (request.getStatus() != null
                && !request.getStatus().isBlank()) {

            subscription.setStatus(
                    request.getStatus()
            );
        }

        subscription.setUpdatedAt(new Date());

        return subscriptionRepository.save(subscription);
    }

    /*
     * Delete subscription
     */
    public void deleteSubscription(String id) {

        if (!subscriptionRepository.existsById(id)) {

            throw new IllegalArgumentException(
                    "Subscription not found: " + id
            );
        }

        subscriptionRepository.deleteById(id);
    }

    /*
     * Get subscriptions by status
     */
    public List<Subscription> getSubscriptionsByStatus(
            String status) {

        return subscriptionRepository.findByStatus(status);
    }
}