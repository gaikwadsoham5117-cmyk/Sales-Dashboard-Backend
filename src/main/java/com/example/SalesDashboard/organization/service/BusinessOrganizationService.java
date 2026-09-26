package com.example.SalesDashboard.organization.service;

import com.example.SalesDashboard.organization.dto.OrganizationCreateRequest;
import com.example.SalesDashboard.organization.dto.OrganizationUpdateRequest;
import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.entity.SubscriptionStatus;
import com.example.SalesDashboard.organization.repository.BusinessOrganizationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessOrganizationService {

    private final BusinessOrganizationRepository organizationRepository;

    /*
     * Create organization
     */
    public BusinessOrganization createOrganization(
            OrganizationCreateRequest request) {

        if (request.getOrganizationName() == null
                || request.getOrganizationName().isBlank()) {

            throw new IllegalArgumentException(
                    "Organization name is required"
            );
        }

        if (organizationRepository
                .existsByNameIgnoreCase(
                        request.getOrganizationName())) {

            throw new IllegalArgumentException(
                    "Organization already exists: "
                            + request.getOrganizationName()
            );
        }

        Date now = new Date();

        BusinessOrganization organization =
                BusinessOrganization.builder()

                        // Generate UUID automatically
                        .id(UUID.randomUUID().toString())

                        .name(
                                request.getOrganizationName()
                        )

                        .subscriptionId(
                                request.getSubscriptionId()
                        )

                        .createdAt(now)

                        .updatedAt(now)

                        .build();

        return organizationRepository.save(organization);
    }

    /*
     * Get all organizations
     */
    public List<BusinessOrganization> getAllOrganizations() {

        return organizationRepository.findAll();
    }

    /*
     * Get organization by ID
     */
    public BusinessOrganization getOrganizationById(
            String id) {

        return organizationRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Organization not found: " + id
                        )
                );
    }

    /*
     * Update organization
     */
    public BusinessOrganization updateOrganization(
            String id,
            OrganizationUpdateRequest request) {

        BusinessOrganization organization =
                getOrganizationById(id);

        if (request.getOrganizationName() != null
                && !request.getOrganizationName().isBlank()) {

            organization.setName(
                    request.getOrganizationName()
            );
        }

        if (request.getSubscriptionId() != null) {

            organization.setSubscriptionId(
                    request.getSubscriptionId()
            );
        }

        // -----------------------------------------------------
        // SUBSCRIPTION STATUS
        //
        // Lets an admin manually move an organization between
        // TRIAL / PAID / UNPAID. Only touches this organization's
        // subscriptionStatus — never touches individual users.
        // -----------------------------------------------------

        if (request.getSubscriptionStatus() != null
                && !request.getSubscriptionStatus().isBlank()) {

            SubscriptionStatus newStatus;

            try {

                newStatus = SubscriptionStatus.valueOf(
                        request.getSubscriptionStatus()
                                .trim()
                                .toUpperCase()
                );

            } catch (IllegalArgumentException e) {

                throw new IllegalArgumentException(
                        "Invalid subscription status: "
                                + request.getSubscriptionStatus()
                                + ". Allowed values: TRIAL, PAID, UNPAID"
                );
            }

            Date now = new Date();

            if (organization.getSubscriptionStatus()
                    != newStatus) {

                organization.setSubscriptionStatus(
                        newStatus
                );

                organization.setSubscriptionUpdatedAt(now);

                /*
                 * First time this organization is ever
                 * given a subscription status.
                 */
                if (organization.getSubscriptionStartedAt()
                        == null) {

                    organization.setSubscriptionStartedAt(now);
                }
            }
        }

        organization.setUpdatedAt(new Date());

        return organizationRepository.save(organization);
    }

    /*
     * Delete organization
     */
    public void deleteOrganization(String id) {

        if (!organizationRepository.existsById(id)) {

            throw new IllegalArgumentException(
                    "Organization not found: " + id
            );
        }

        organizationRepository.deleteById(id);
    }
}