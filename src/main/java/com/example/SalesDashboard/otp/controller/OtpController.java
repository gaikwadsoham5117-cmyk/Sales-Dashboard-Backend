package com.example.SalesDashboard.otp.controller;

import com.example.SalesDashboard.framework.service.OtpService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Otp")
@CrossOrigin("*")
@RestController
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/api/otp/generate/email")
    public ResponseEntity<?> generateOtpForEmail(@RequestParam String email) {
        otpService.generateOtpForEmail(email);
        return ResponseEntity.ok(Map.of("message", "OTP has been sent to your email ."));
    }

    @PostMapping("/api/otp/validate")
    public ResponseEntity<?> validateOtp(@RequestParam String email, @RequestParam String otp) {
        String token = otpService.validateOtp(email, otp);

        if (token != null) {
            return ResponseEntity.ok(Map.of(
                    "message", "OTP is valid. Authentication successful.",
                    "token", token
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid OTP. Please try again."));
        }
    }
}
