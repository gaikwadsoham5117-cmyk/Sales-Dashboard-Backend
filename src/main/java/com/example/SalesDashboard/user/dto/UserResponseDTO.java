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
    private String street;
    private String apartment;
    private String pinCode;
    private String city;
    private String district;
    private UserRoles roles;
    private UserStatus status;

    public UserResponseDTO(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.mobile = user.getMobile();
        this.street = user.getStreet();
        this.pinCode = user.getPinCode();
        this.apartment = user.getApartment();
        this.address = user.getAddress();
        this.city = user.getCity();
        this.district = user.getDistrict();
        this.roles = user.getRoles();
        this.status = user.getStatus();
    }
}

