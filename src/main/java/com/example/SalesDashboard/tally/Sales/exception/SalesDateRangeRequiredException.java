package com.example.SalesDashboard.tally.Sales.exception;

public class SalesDateRangeRequiredException extends RuntimeException {
    public SalesDateRangeRequiredException(String message) {
        super(message);
    }
}