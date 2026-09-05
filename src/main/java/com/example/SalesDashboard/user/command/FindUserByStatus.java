package com.example.SalesDashboard.user.command;

import com.example.SalesDashboard.user.entity.UserStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FindUserByStatus {
    private List<UserStatus> status;
}
