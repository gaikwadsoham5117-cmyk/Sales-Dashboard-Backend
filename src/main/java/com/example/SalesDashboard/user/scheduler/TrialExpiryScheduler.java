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

/**
 * Subscription status lives on the ORGANIZATION now, not on
 * every individual user. This scheduler only ever updates
 * BusinessOrganization.subscriptionStatus, never User.status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrialExpiryScheduler {

    private final BusinessOrganizationRepository organizationRepository;

    /**
     * Runs every day at 12:05 AM.
     *
     * Organizations in TRIAL status for more than 15 days
     * are automatically changed to UNPAID.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void expireTrialOrganizations() {

        Date expiryTime = new Date(
                System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)
        );

        List<BusinessOrganization> expiredOrganizations =
                organizationRepository.findBySubscriptionStatusAndSubscriptionStartedAtBefore(
                        SubscriptionStatus.TRIAL,
                        expiryTime
                );

        if (expiredOrganizations.isEmpty()) {
            log.info("Trial expiry scheduler: no expired trial organizations found.");
            return;
        }

        Date now = new Date();

        for (BusinessOrganization organization : expiredOrganizations) {

            organization.setSubscriptionStatus(SubscriptionStatus.UNPAID);
            organization.setSubscriptionUpdatedAt(now);
        }

        organizationRepository.saveAll(expiredOrganizations);

        log.info(
                "Trial expiry scheduler: {} organization(s) changed from TRIAL to UNPAID.",
                expiredOrganizations.size()
        );
    }
}