package com.example.SalesDashboard.user.entity;

import com.example.SalesDashboard.framework.model.UserRoles;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;


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
    private String street;
    private String apartment;
    private String pinCode;
    private String city;
    private String district;
    private UserRoles roles;
    private UserStatus status;
    private Date createdOn;
}
