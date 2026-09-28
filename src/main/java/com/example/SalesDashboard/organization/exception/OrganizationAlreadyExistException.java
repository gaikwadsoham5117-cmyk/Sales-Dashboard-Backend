package com.example.SalesDashboard.organization.exception;

public class OrganizationAlreadyExistException extends RuntimeException {
    public OrganizationAlreadyExistException(String message) {
        super(message);
    }
}