package com.example.SalesDashboard.tally.Sales.controller;

import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
import com.example.SalesDashboard.tally.Sales.service.TallyService;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tally")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/tally")
public class TallyController {

    private final TallyService tallyService;


    // =========================================================
    // DEFAULT COMPANY
    // =========================================================

    @GetMapping("/sales-vouchers")
    public ResponseEntity<List<SalesVoucherDTO>> getAllSalesVouchers(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                tallyService.pullAllSalesVouchers(
                        extractUserId(authentication)
                )
        );
    }


    // =========================================================
    // COMPANY-WISE
    // =========================================================

    @GetMapping("/{companyName}/sales-vouchers")
    public ResponseEntity<List<SalesVoucherDTO>> getSalesVouchersByCompany(
            @PathVariable String companyName,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                tallyService.pullAllSalesVouchers(
                        extractUserId(authentication),
                        companyName
                )
        );
    }


    // =========================================================
    // DEFAULT COMPANY - RAW
    // =========================================================

    @GetMapping("/sales-vouchers/raw")
    public ResponseEntity<JsonNode> getAllSalesVouchersRaw(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                tallyService.pullAllSalesVouchersRaw(
                        extractUserId(authentication)
                )
        );
    }


    // =========================================================
    // COMPANY-WISE - RAW
    // =========================================================

    @GetMapping("/{companyName}/sales-vouchers/raw")
    public ResponseEntity<JsonNode> getSalesVouchersRawByCompany(
            @PathVariable String companyName,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                tallyService.pullAllSalesVouchersRaw(
                        extractUserId(authentication),
                        companyName
                )
        );
    }


    /**
     * authentication.getName() resolves to the user's email, not the
     * Mongo _id that Agent.userId / AgentRelayService key off of. The
     * real id is a JWT claim attached by JwtAuthenticationFilter.
     */
    private String extractUserId(Authentication authentication) {

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