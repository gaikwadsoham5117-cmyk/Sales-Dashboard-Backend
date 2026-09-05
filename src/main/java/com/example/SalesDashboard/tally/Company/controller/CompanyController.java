package com.example.SalesDashboard.tally.Company.controller;

import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.tally.Company.dto.CompanyDto;
import com.example.SalesDashboard.tally.Company.service.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }


    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllCompanies(Authentication authentication) {
        List<CompanyDto> companies = companyService.pullAllCompanies(extractUserId(authentication));
        return ResponseEntity.ok(response(true, companies.size() + " compan(y/ies) loaded in Tally", companies));
    }


    @GetMapping("/all/raw")
    public ResponseEntity<Map<String, Object>> getAllCompaniesRaw(Authentication authentication) {
        String rawResponse = companyService.pullAllCompaniesRaw(extractUserId(authentication));
        return ResponseEntity.ok(response(true, "Raw company list pulled from Tally", rawResponse));
    }

    private Map<String, Object> response(boolean success, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    /**
     * Same pattern as TallyController/AgentController: authentication.getName()
     * is the user's email, the real Mongo userId is a JWT claim.
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