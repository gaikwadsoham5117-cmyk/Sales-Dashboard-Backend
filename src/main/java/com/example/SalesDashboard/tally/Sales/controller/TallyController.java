package com.example.SalesDashboard.tally.Sales.controller;

import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
import com.example.SalesDashboard.tally.Sales.service.TallyService;
import com.example.SalesDashboard.user.exception.UserNotAuthenticatedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    // COMPANY-WISE SALES VOUCHERS
    // =========================================================
    //
    // GET:
    // /api/tally/{companyName}/sales-vouchers
    //
    // No agentId required.
    //
    // Backend automatically identifies the authenticated user,
    // organization and connected Tally Agent.
    //
    // =========================================================

    @Operation(
            summary = "Get all sales vouchers for a company",
            description = "Fetches sales vouchers from the connected Tally Agent automatically."
    )
    @GetMapping("/{companyName}/sales-vouchers")
    public ResponseEntity<List<SalesVoucherDTO>> getSalesVouchersByCompany(

            @Parameter(
                    description = "Tally company name",
                    required = true
            )
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
    // DATE-RANGE SALES VOUCHERS
    // =========================================================
    //
    // DEFAULT:
    //
    // GET:
    // /api/tally/{companyName}/sales-vouchers/date-range
    //
    // from = today - 1 month
    // to   = today
    //
    // CUSTOM:
    //
    // /api/tally/{companyName}/sales-vouchers/date-range
    //     ?from=2026-08-01
    //     &to=2026-08-31
    //
    // No agentId required.
    //
    // =========================================================

    @Operation(
            summary = "Get sales vouchers by date range",
            description = "Fetches sales vouchers from the connected Tally Agent automatically for the specified date range."
    )
    @GetMapping("/{companyName}/sales-vouchers/date-range")
    public ResponseEntity<List<SalesVoucherDTO>> getSalesVouchersByDateRange(

            @Parameter(
                    description = "Tally company name",
                    required = true
            )
            @PathVariable String companyName,

            @Parameter(
                    description = "Start date in yyyy-MM-dd format"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @Parameter(
                    description = "End date in yyyy-MM-dd format"
            )
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            Authentication authentication
    ) {

        String userId = extractUserId(authentication);

        LocalDate today = LocalDate.now();

        // If 'to' is not supplied, use today's date.
        if (to == null) {
            to = today;
        }

        // If 'from' is not supplied, use one month before 'to'.
        if (from == null) {
            from = to.minusMonths(1);
        }

        // Validate date range.
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'from' date cannot be after 'to' date"
            );
        }

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

        // -----------------------------------------------------
        // First try JWT authentication details
        // -----------------------------------------------------

        Object details = authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter.JwtAuthenticationDetails jwtDetails) {

            String userId = jwtDetails.getUserId();

            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }

        // -----------------------------------------------------
        // Fallback to authentication name
        // -----------------------------------------------------

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