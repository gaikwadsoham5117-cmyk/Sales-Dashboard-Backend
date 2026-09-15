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
public class SubscriptionExpiryScheduler {

    private final UserRepository userRepository;

    /**
     * Runs every day at 12:15 AM.
     *
     * PAID users whose subscription has been PAID
     * for more than one year are automatically changed
     * to UNPAID.
     */
    @Scheduled(cron = "0 15 0 * * *")
    public void expirePaidSubscriptions() {

        Date expiryTime = new Date(
                System.currentTimeMillis()
                        - (365L * 24 * 60 * 60 * 1000)
        );

        List<User> expiredUsers =
                userRepository.findByStatusAndStatusUpdatedAtBefore(
                        UserStatus.PAID,
                        expiryTime
                );

        if (expiredUsers.isEmpty()) {
            log.info(
                    "Subscription expiry scheduler: no expired PAID subscriptions found."
            );
            return;
        }

        Date now = new Date();

        for (User user : expiredUsers) {

            user.setStatus(UserStatus.UNPAID);
            user.setStatusUpdatedAt(now);
        }

        userRepository.saveAll(expiredUsers);

        log.info(
                "Subscription expiry scheduler: {} user(s) changed from PAID to UNPAID.",
                expiredUsers.size()
        );
    }
}