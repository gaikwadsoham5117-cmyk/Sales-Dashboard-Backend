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

@Document(collection = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    private UserStatus status;

    /*
     * Existing field.
     * Keep this because existing code already uses createdOn.
     */
    private Date createdOn;

    /*
     * Subscription/trial creation date.
     *
     * For a new user:
     * createdAt = registration date/time
     */
    private Date createdAt;

    /*
     * Last subscription status change.
     *
     * TRIAL  -> initial registration time
     * UNPAID -> when trial/subscription expires
     * PAID   -> when admin activates/renews subscription
     */
    private Date statusUpdatedAt;
}