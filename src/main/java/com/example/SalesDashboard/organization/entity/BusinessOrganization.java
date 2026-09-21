package com.example.SalesDashboard.organization.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "business_organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessOrganization {

    @Id
    private String id;

    private String organizationName;

    private String subscriptionId;

    private Date createdAt;

    private Date statusUpdatedAt;
}