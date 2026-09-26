package com.example.SalesDashboard.user.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.example.SalesDashboard.framework.model.UserRoles;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String email;

    private String password;

    private String firstName;

    private String lastName;

    private String mobile;

    private String address;

    private UserRoles roles;

    @Builder.Default
    private Boolean enabled = true;

    private UserStatus status;

    private String organizationId;

    private Date createdOn;

    private Date createdAt;

    private Date statusUpdatedAt;
}