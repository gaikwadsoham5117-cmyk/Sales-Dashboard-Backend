package com.example.SalesDashboard.user.scheduler;

import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.entity.SubscriptionStatus;
import com.example.SalesDashboard.organization.repository.BusinessOrganizationRepository;

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

    private final BusinessOrganizationRepository organizationRepository;

    /**
     * Runs every day at 12:15 AM.
     *
     */
    @Scheduled(cron = "0 15 0 * * *")
//    @Scheduled(cron = "0 * * * * *")

    public void expirePaidSubscriptions() {

        Date expiryTime = new Date(
                System.currentTimeMillis()
                        - (365L * 24 * 60 * 60 * 1000)
        );

        List<BusinessOrganization> expiredOrganizations =
                organizationRepository.findBySubscriptionStatusAndSubscriptionUpdatedAtBefore(
                        SubscriptionStatus.PAID,
                        expiryTime
                );

        if (expiredOrganizations.isEmpty()) {
            log.info(
                    "Subscription expiry scheduler: no expired PAID organizations found."
            );
            return;
        }

        Date now = new Date();

        for (BusinessOrganization organization : expiredOrganizations) {

            organization.setSubscriptionStatus(SubscriptionStatus.UNPAID);
            organization.setSubscriptionUpdatedAt(now);
        }

        organizationRepository.saveAll(expiredOrganizations);

        log.info(
                "Subscription expiry scheduler: {} organization(s) changed from PAID to UNPAID.",
                expiredOrganizations.size()
        );
    }
}