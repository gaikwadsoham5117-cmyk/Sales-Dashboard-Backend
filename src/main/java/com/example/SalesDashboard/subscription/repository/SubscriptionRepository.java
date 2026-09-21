package com.example.SalesDashboard.subscription.repository;

import com.example.SalesDashboard.subscription.entity.Subscription;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository
        extends MongoRepository<Subscription, String> {

    List<Subscription> findByStatus(String status);
}