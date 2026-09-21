package com.example.SalesDashboard.subscription.controller;

import com.example.SalesDashboard.subscription.dto.SubscriptionCreateRequest;
import com.example.SalesDashboard.subscription.dto.SubscriptionUpdateRequest;
import com.example.SalesDashboard.subscription.entity.Subscription;
import com.example.SalesDashboard.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Subscriptions")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/Create")
    public ResponseEntity<Subscription> createSubscription(
            @RequestBody SubscriptionCreateRequest request) {

        return ResponseEntity.ok(
                subscriptionService.createSubscription(request)
        );
    }

    @GetMapping("/lookup/all")
    public ResponseEntity<List<Subscription>>
    getAllSubscriptions() {

        return ResponseEntity.ok(
                subscriptionService.getAllSubscriptions()
        );
    }

    @GetMapping("/lookup/{id}")
    public ResponseEntity<Subscription>
    getSubscriptionById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                subscriptionService.getSubscriptionById(id)
        );
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<Subscription>
    updateSubscription(
            @PathVariable String id,
            @RequestBody SubscriptionUpdateRequest request) {

        return ResponseEntity.ok(
                subscriptionService.updateSubscription(
                        id,
                        request
                )
        );
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable String id) {

        subscriptionService.deleteSubscription(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Subscription>>
    getSubscriptionsByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                subscriptionService
                        .getSubscriptionsByStatus(status)
        );
    }
}