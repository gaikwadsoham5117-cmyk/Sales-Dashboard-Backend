package com.example.SalesDashboard.user.dto;

import com.example.SalesDashboard.framework.model.UserRoles;
import com.example.SalesDashboard.user.entity.UserStatus;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    @Id
    private String id;
    private String email = "";
    private String password = "";
    private String firstName = "";
    private String lastName = "";
    private String mobile = "";
    private String address = "";
    private UserRoles roles = UserRoles.USER;
    private UserStatus status = UserStatus.ACTIVE;
    private Date createdOn;
}
