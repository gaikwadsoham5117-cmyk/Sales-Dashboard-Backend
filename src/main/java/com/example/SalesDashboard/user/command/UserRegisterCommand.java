package com.example.SalesDashboard.user.command;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRegisterCommand {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String mobile;
    private String address;
    private String message;
    private Date createdOn;
}

