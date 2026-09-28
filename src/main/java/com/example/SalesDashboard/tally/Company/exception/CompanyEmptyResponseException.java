package com.example.SalesDashboard.tally.Company.exception;

public class CompanyEmptyResponseException extends RuntimeException {
    public CompanyEmptyResponseException(String message) {
        super(message);
    }
}