package com.example.SalesDashboard.subscription.exception;

public class SubscriptionPlanNameRequiredException extends RuntimeException {
    public SubscriptionPlanNameRequiredException(String message) {
        super(message);
    }
}