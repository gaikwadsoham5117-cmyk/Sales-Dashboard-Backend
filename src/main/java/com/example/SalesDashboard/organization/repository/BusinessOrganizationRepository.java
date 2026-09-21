package com.example.SalesDashboard.organization.repository;

import com.example.SalesDashboard.organization.entity.BusinessOrganization;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessOrganizationRepository
        extends MongoRepository<BusinessOrganization, String> {

    boolean existsByOrganizationNameIgnoreCase(
            String organizationName
    );
}