package com.example.SalesDashboard.organization.controller;

import com.example.SalesDashboard.organization.dto.OrganizationCreateRequest;
import com.example.SalesDashboard.organization.dto.OrganizationUpdateRequest;
import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.service.BusinessOrganizationService;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Organizations")
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class BusinessOrganizationController {

    private final BusinessOrganizationService organizationService;

    @PostMapping("/create")
    public ResponseEntity<BusinessOrganization> createOrganization(
            @RequestBody OrganizationCreateRequest request) {

        return ResponseEntity.ok(
                organizationService.createOrganization(request)
        );
    }

    @GetMapping("/lookup/all")
    public ResponseEntity<List<BusinessOrganization>>
    getAllOrganizations() {

        return ResponseEntity.ok(
                organizationService.getAllOrganizations()
        );
    }

    @GetMapping("/lookup/{id}")
    public ResponseEntity<BusinessOrganization>
    getOrganizationById(@PathVariable String id) {

        return ResponseEntity.ok(
                organizationService.getOrganizationById(id)
        );
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<BusinessOrganization>
    updateOrganization(
            @PathVariable String id,
            @RequestBody OrganizationUpdateRequest request) {

        return ResponseEntity.ok(
                organizationService.updateOrganization(
                        id,
                        request
                )
        );
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteOrganization(
            @PathVariable String id) {

        organizationService.deleteOrganization(id);

        return ResponseEntity.noContent().build();
    }
}