package com.example.SalesDashboard.organization.service;

import com.example.SalesDashboard.organization.dto.OrganizationCreateRequest;
import com.example.SalesDashboard.organization.dto.OrganizationUpdateRequest;
import com.example.SalesDashboard.organization.entity.BusinessOrganization;
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
            .existsByOrganizationNameIgnoreCase(
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

                    .organizationName(
                            request.getOrganizationName()
                    )

                    .subscriptionId(
                            request.getSubscriptionId()
                    )

                    .createdAt(now)

                    .statusUpdatedAt(now)

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

            organization.setOrganizationName(
                    request.getOrganizationName()
            );
        }

        if (request.getSubscriptionId() != null) {

            organization.setSubscriptionId(
                    request.getSubscriptionId()
            );
        }

        organization.setStatusUpdatedAt(new Date());

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