package com.example.SalesDashboard.tally.Sales.controller;

import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
import com.example.SalesDashboard.tally.Sales.service.TallyService;
import com.example.SalesDashboard.user.exception.UserNotAuthenticatedException;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Sales")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tally")
public class TallyController {

    private final TallyService tallyService;

    // =========================================================
    // EXISTING COMPANY-WISE SALES VOUCHERS API
    // =========================================================
    //
    // EXISTING API - DO NOT CHANGE
    //
    // GET:
    // /api/tally/{companyName}/sales-vouchers
    //
    // =========================================================

    @GetMapping("/{companyName}/sales-vouchers")
    public ResponseEntity<List<SalesVoucherDTO>> getSalesVouchersByCompany(
            @PathVariable String companyName,
            Authentication authentication
    ) {

        String userId = extractUserId(authentication);

        return ResponseEntity.ok(
                tallyService.pullAllSalesVouchers(
                        userId,
                        companyName
                )
        );
    }

    // =========================================================
    // NEW DATE-RANGE SALES VOUCHERS API
    // =========================================================
    //
    // DEFAULT:
    //
    // GET
    // /api/tally/{companyName}/sales-vouchers/date-range
    //
    // Default:
    // from = today - 1 month
    // to   = today
    //
    // CUSTOM:
    //
    // GET
    // /api/tally/{companyName}/sales-vouchers/date-range
    // ?from=2026-08-01&to=2026-08-31
    //
    // Date format:
    // yyyy-MM-dd
    //
    // =========================================================

    @GetMapping("/{companyName}/sales-vouchers/date-range")
    public ResponseEntity<List<SalesVoucherDTO>> getSalesVouchersByDateRange(
            @PathVariable String companyName,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            Authentication authentication
    ) {

        String userId = extractUserId(authentication);

        // -----------------------------------------------------
        // TODAY
        // -----------------------------------------------------

        LocalDate today = LocalDate.now();

        // -----------------------------------------------------
        // DEFAULT TO DATE
        // -----------------------------------------------------

        if (to == null) {
            to = today;
        }

        // -----------------------------------------------------
        // DEFAULT FROM DATE
        // -----------------------------------------------------

        if (from == null) {
            from = to.minusMonths(1);
        }

        // -----------------------------------------------------
        // DATE VALIDATION
        // -----------------------------------------------------

        if (from.isAfter(to)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'from' date cannot be after 'to' date"
            );
        }

        // -----------------------------------------------------
        // CALL DATE-RANGE SERVICE
        // -----------------------------------------------------

        return ResponseEntity.ok(
                tallyService.pullSalesVouchersByDateRange(
                        userId,
                        companyName,
                        from,
                        to
                )
        );
    }

    // =========================================================
    // USER ID EXTRACTION
    // =========================================================

    private String extractUserId(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new UserNotAuthenticatedException(
                    "Authentication is required"
            );
        }

        Object details = authentication.getDetails();

        if (details instanceof JwtAuthenticationFilter.JwtAuthenticationDetails jwtDetails) {

            String userId = jwtDetails.getUserId();

            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }

        String authenticationName =
                authentication.getName();

        if (authenticationName == null
                || authenticationName.isBlank()) {

            throw new UserNotAuthenticatedException(
                    "Unable to determine authenticated user"
            );
        }

        return authenticationName;
    }
}