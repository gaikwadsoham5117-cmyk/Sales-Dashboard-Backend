package com.example.SalesDashboard.user.command;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeStatusRequest {

    /*
     * true  = ENABLE employee
     * false = DISABLE employee
     */
    @NotNull
    private Boolean enabled;
}