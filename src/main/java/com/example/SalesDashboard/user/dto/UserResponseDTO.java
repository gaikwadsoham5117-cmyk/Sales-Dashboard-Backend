package com.example.SalesDashboard.user.dto;

import com.example.SalesDashboard.framework.model.UserRoles;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDTO {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String mobile;
    private String address;
    private UserRoles roles;
    private UserStatus status;

    public UserResponseDTO(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.mobile = user.getMobile();
        this.address = user.getAddress();
        this.roles = user.getRoles();
        this.status = user.getStatus();
    }
}

