package com.example.SalesDashboard.user.command;

import com.example.SalesDashboard.user.entity.UserStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateCommand {

    private String firstName;
    private String lastName;
    private String mobile;
    private String address;
    private UserStatus status;
}
