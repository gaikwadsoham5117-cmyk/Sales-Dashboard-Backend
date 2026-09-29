package com.example.SalesDashboard.tally.Sales.exception;

public class SalesCompanyNameRequiredException extends RuntimeException {
    public SalesCompanyNameRequiredException(String message) {
        super(message);
    }
}