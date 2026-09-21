package com.example.SalesDashboard.user.command;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerCreateRequest {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String mobile;
    private String address;

    /*
     * Organization to which this Owner belongs.
     */
    private String organizationId;
}