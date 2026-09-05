package com.example.SalesDashboard.user.controller;

import com.example.SalesDashboard.framework.dto.AuthRequest;
import com.example.SalesDashboard.framework.dto.AuthResponse;
import com.example.SalesDashboard.framework.service.AuthService;
import com.example.SalesDashboard.user.command.*;
import com.example.SalesDashboard.user.dto.UserResponseDTO;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/api/user/register")
    public ResponseEntity<UserRegisterCommand> register(@Valid @RequestBody UserRegisterCommand request) {
        UserRegisterCommand response = authService.registerUser(request);
        response.setPassword(null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/user/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PutMapping("/api/user/{id}/update/allfields")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable String id,@RequestBody UserUpdateCommand updateCommand) {
        return ResponseEntity.ok(userService.updateUserRequiredFields(id, updateCommand));
    }

    @GetMapping("/api/user/{id}/lookup")
    public ResponseEntity<User> findUserById(@PathVariable String id) {
        return ResponseEntity.of(Optional.ofNullable(userService.findUserById(id)));
    }

    @GetMapping("/api/user/lookup/all")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.lookUpAllUsers());
    }

    @GetMapping("/api/user/lookup/all/active")
    public ResponseEntity<List<User>> getAllActiveUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }

    @PutMapping("/api/user/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        String message = userService.generateResetCodeAndSendToEmail(email);
        return ResponseEntity.ok(message);
    }

    @PutMapping("/api/user/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordCommand request) {
        String message = userService.resetPasswordAfterVerification(request);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/api/user/lookup/status")
    public ResponseEntity<List<User>> getUsersByStatus(@ModelAttribute FindUserByStatus filter) {
        return ResponseEntity.ok(userService.getUsersByStatus(filter.getStatus()));
    }

    @GetMapping("/api/user/lookup/all/users/{id}")
    public Object getAllUsersById(@PathVariable String id) {
        return userService.findAllUsersById(id);
    }

}
