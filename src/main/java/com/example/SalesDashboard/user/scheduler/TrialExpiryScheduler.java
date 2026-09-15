package com.example.SalesDashboard.user.scheduler;

import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import com.example.SalesDashboard.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrialExpiryScheduler {

    private final UserRepository userRepository;

    /**
     * Runs every day at 12:05 AM.
     *
     * Users in TRIAL status for more than 15 days
     * are automatically changed to UNPAID.
     */
    // @Scheduled(cron = "0 * * * * *")
    @Scheduled(cron = "0 15 0 * * *")
    public void expireTrialUsers() {

        Date expiryTime = new Date(
                System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)
        );

        List<User> expiredUsers =
                userRepository.findByStatusAndCreatedAtBefore(
                        UserStatus.TRIAL,
                        expiryTime
                );

        if (expiredUsers.isEmpty()) {
            log.info("Trial expiry scheduler: no expired trial users found.");
            return;
        }

        Date now = new Date();

        for (User user : expiredUsers) {

            user.setStatus(UserStatus.UNPAID);
            user.setStatusUpdatedAt(now);
        }

        userRepository.saveAll(expiredUsers);

        log.info(
                "Trial expiry scheduler: {} user(s) changed from TRIAL to UNPAID.",
                expiredUsers.size()
        );
    }
}