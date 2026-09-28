package com.example.SalesDashboard.organization.exception;

public class OrganizationNameRequiredException extends RuntimeException {
    public OrganizationNameRequiredException(String message) {
        super(message);
    }
}