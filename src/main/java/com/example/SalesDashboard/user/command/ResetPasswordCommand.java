package com.example.SalesDashboard.user.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordCommand {
    private String email;
    private String resetCode;
    private String newPassword;
    private String confirmPassword;
}
