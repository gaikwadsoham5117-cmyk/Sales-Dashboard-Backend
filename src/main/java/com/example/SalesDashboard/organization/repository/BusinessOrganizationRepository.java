package com.example.SalesDashboard.organization.repository;

import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.entity.SubscriptionStatus;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface BusinessOrganizationRepository
        extends MongoRepository<BusinessOrganization, String> {

    boolean existsByNameIgnoreCase(String name);

    List<BusinessOrganization> findBySubscriptionStatus(
            SubscriptionStatus subscriptionStatus
    );

    List<BusinessOrganization> findBySubscriptionStatusNot(
            SubscriptionStatus subscriptionStatus
    );

    /*
     * Used by the trial-expiry scheduler: organizations still
     * on TRIAL whose trial started before the cutoff date.
     */
    List<BusinessOrganization> findBySubscriptionStatusAndSubscriptionStartedAtBefore(
            SubscriptionStatus subscriptionStatus,
            Date cutoff
    );

    /*
     * Used by the subscription-expiry scheduler: organizations
     * still PAID whose subscription was last updated before the
     * cutoff date (i.e. hasn't been renewed).
     */
    List<BusinessOrganization> findBySubscriptionStatusAndSubscriptionUpdatedAtBefore(
            SubscriptionStatus subscriptionStatus,
            Date cutoff
    );
}