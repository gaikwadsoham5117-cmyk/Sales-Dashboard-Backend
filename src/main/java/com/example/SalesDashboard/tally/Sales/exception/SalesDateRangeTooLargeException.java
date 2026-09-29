package com.example.SalesDashboard.tally.Sales.exception;

public class SalesDateRangeTooLargeException extends RuntimeException {
    public SalesDateRangeTooLargeException(String message) {
        super(message);
    }
}