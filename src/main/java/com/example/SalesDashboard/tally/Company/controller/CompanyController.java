package com.example.SalesDashboard.tally.Company.controller;

import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.tally.Company.dto.CompanyDto;
import com.example.SalesDashboard.tally.Company.service.CompanyService;
import com.example.SalesDashboard.user.exception.UserNotAuthenticatedException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllCompanies(
            Authentication authentication
    ) {

        String userId = extractUserId(authentication);

        List<CompanyDto> companies =
                companyService.pullAllCompanies(userId);

        return ResponseEntity.ok(
                response(
                        true,
                        companies.size() + " compan(y/ies) loaded in Tally",
                        companies
                )
        );
    }

    private Map<String, Object> response(
            boolean success,
            String message,
            Object data
    ) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("success", success);
        body.put("message", message);
        body.put("data", data);

        return body;
    }

    private String extractUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UserNotAuthenticatedException("User is not authenticated");
        }

        Object details = authentication.getDetails();

        if (details instanceof JwtAuthenticationFilter.JwtAuthenticationDetails jwtDetails) {

            String userId = jwtDetails.getUserId();

            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }

        return authentication.getName();
    }
}