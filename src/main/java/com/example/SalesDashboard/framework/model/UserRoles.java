package com.example.SalesDashboard.framework.model;

import java.util.Arrays;

public enum UserRoles {

    USER,
    ADMIN,
    OWNER,
    EMPLOYEE;

    public static String[] ALL() {

        return Arrays.stream(UserRoles.values())
                .map(Enum::name)
                .toArray(String[]::new);
    }
}