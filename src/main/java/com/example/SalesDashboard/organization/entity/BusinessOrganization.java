package com.example.SalesDashboard.organization.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "business_organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessOrganization {

    @Id
    private String id;

    private String name;

    private String email;

    private String mobile;

    private String address;

    /*
     * Existing subscription reference.
     *
     * This points to the Subscription collection.
     */
    private String subscriptionId;

    /*
     * Subscription status belongs to ORGANIZATION.
     *
     * TRIAL
     * PAID
     * UNPAID
     */
    private SubscriptionStatus subscriptionStatus;

    /*
     * When the organization's subscription/trial
     * was started.
     */
    private Date subscriptionStartedAt;

    /*
     * Last time subscription status was changed.
     */
    private Date subscriptionUpdatedAt;

    /*
     * Organization creation date.
     */
    private Date createdAt;

    /*
     * Organization last update date.
     */
    private Date updatedAt;
}