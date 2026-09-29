package com.example.SalesDashboard.user.exception;

public class OrganizationIdRequiredException extends RuntimeException {
    public OrganizationIdRequiredException(String message) {
        super(message);
    }
}