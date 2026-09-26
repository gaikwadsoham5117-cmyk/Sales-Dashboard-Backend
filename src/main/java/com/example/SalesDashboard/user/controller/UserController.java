package com.example.SalesDashboard.user.controller;

import com.example.SalesDashboard.framework.dto.AuthRequest;
import com.example.SalesDashboard.framework.dto.AuthResponse;
import com.example.SalesDashboard.framework.service.AuthService;
import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;

import com.example.SalesDashboard.user.command.*;
import com.example.SalesDashboard.user.dto.UserResponseDTO;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.exception.UserNotAuthenticatedException;
import com.example.SalesDashboard.user.service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "User")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final AuthService authService;


    // ============================================================
    // REGISTER USER
    // ============================================================

    @PostMapping("/api/user/register")
    public ResponseEntity<UserRegisterCommand> register(
            @Valid @RequestBody UserRegisterCommand request
    ) {

        UserRegisterCommand response =
                authService.registerUser(request);

        response.setPassword(null);

        return ResponseEntity.ok(response);
    }


    // ============================================================
    // LOGIN
    // ============================================================

    @PostMapping("/api/user/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody AuthRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }


    // ============================================================
    // UPDATE USER
    // ============================================================

    @PutMapping("/api/user/{id}/update/allfields")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable String id,
            @RequestBody UserUpdateCommand updateCommand
    ) {

        return ResponseEntity.ok(
                userService.updateUserRequiredFields(
                        id,
                        updateCommand
                )
        );
    }


    // ============================================================
    // FIND USER BY ID
    // ============================================================

    @GetMapping("/api/user/{id}/lookup")
    public ResponseEntity<User> findUserById(
            @PathVariable String id
    ) {

        return ResponseEntity.of(
                Optional.ofNullable(
                        userService.findUserById(id)
                )
        );
    }


    // ============================================================
    // GET ALL USERS
    // ============================================================

    @GetMapping("/api/user/lookup/all")
    public ResponseEntity<List<User>> getAllUsers() {

        return ResponseEntity.ok(
                userService.lookUpAllUsers()
        );
    }


    // ============================================================
    // GET ACTIVE USERS
    // ============================================================

    @GetMapping("/api/user/lookup/all/active")
    public ResponseEntity<List<User>> getAllActiveUsers() {

        return ResponseEntity.ok(
                userService.getAllActiveUsers()
        );
    }


    // ============================================================
    // FORGOT PASSWORD
    // ============================================================

    @PutMapping("/api/user/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @RequestParam String email
    ) {

        String message =
                userService.generateResetCodeAndSendToEmail(
                        email
                );

        return ResponseEntity.ok(message);
    }


    // ============================================================
    // RESET PASSWORD
    // ============================================================

    @PutMapping("/api/user/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestBody ResetPasswordCommand request
    ) {

        String message =
                userService.resetPasswordAfterVerification(
                        request
                );

        return ResponseEntity.ok(message);
    }


    // ============================================================
    // USERS BY STATUS
    // ============================================================

    @GetMapping("/api/user/lookup/status")
    public ResponseEntity<List<User>> getUsersByStatus(
            @ModelAttribute FindUserByStatus filter
    ) {

        return ResponseEntity.ok(
                userService.getUsersByStatus(
                        filter.getStatus()
                )
        );
    }


    // ============================================================
    // USERS BY ID
    // ============================================================

    @GetMapping("/api/user/lookup/all/users/{id}")
    public ResponseEntity<List<User>> getAllUsersById(
            @PathVariable String id
    ) {

        return ResponseEntity.ok(
                userService.findAllUsersById(id)
        );
    }


    // ============================================================
    // CREATE OWNER
    // ============================================================

    @PostMapping("/api/owners/register")
    public ResponseEntity<UserResponseDTO> createOwner(
            @Valid @RequestBody OwnerCreateRequest request
    ) {

        return ResponseEntity.ok(
                userService.createOwner(request)
        );
    }


    // ============================================================
    // CREATE EMPLOYEE
    // ============================================================

    @PostMapping("/api/employees/register")
    public ResponseEntity<UserResponseDTO> createEmployee(
            @Valid @RequestBody EmployeeCreateRequest request,
            Authentication authentication
    ) {

        String ownerUserId =
                extractUserId(authentication);

        return ResponseEntity.ok(
                userService.createEmployee(
                        ownerUserId,
                        request
                )
        );
    }

    @DeleteMapping("/api/employees/{employeeId}")
    public ResponseEntity<String> deleteEmployee(
            @PathVariable String employeeId,
            Authentication authentication
    ) {

        String ownerUserId =
                extractUserId(authentication);

        userService.deleteEmployee(
                ownerUserId,
                employeeId
        );

        return ResponseEntity.ok(
                "Employee deleted successfully"
        );
    }


    // ============================================================
    // EXTRACT AUTHENTICATED USER ID
    // ============================================================

    private String extractUserId(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new UserNotAuthenticatedException(
                    "Authentication is required"
            );
        }

        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter.JwtAuthenticationDetails jwtDetails) {

            String userId =
                    jwtDetails.getUserId();

            if (userId != null
                    && !userId.isBlank()) {

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

    @PutMapping("/api/employees/{employeeId}/status")
    public ResponseEntity<UserResponseDTO> changeEmployeeStatus(
            @PathVariable String employeeId,
            @Valid @RequestBody EmployeeStatusRequest request,
            Authentication authentication
    ) {

        String ownerUserId =
                extractUserId(authentication);

        return ResponseEntity.ok(
                userService.changeEmployeeStatus(
                        ownerUserId,
                        employeeId,
                        request.getEnabled()
                )
        );
    }
}