package com.example.SalesDashboard.framework.service;

import com.example.SalesDashboard.framework.dto.AuthRequest;
import com.example.SalesDashboard.framework.dto.AuthResponse;
import com.example.SalesDashboard.framework.security.JwtUtil;
import com.example.SalesDashboard.user.command.UserRegisterCommand;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import com.example.SalesDashboard.user.exception.InactiveAccountException;
import com.example.SalesDashboard.user.exception.InvalidCredentialsException;
import com.example.SalesDashboard.user.repository.UserRepository;
import com.example.SalesDashboard.user.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;

    private final UserService userService;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserRegisterCommand registerUser(UserRegisterCommand command) {

        return userService.registerUser(command);
    }

    public AuthResponse login(AuthRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        )
                );

        // Validate password
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {

            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        /*
         * Subscription / Account status check
         *
         * TRIAL    -> Login allowed
         * PAID     -> Login allowed
         * ACTIVE   -> Login allowed (existing users)
         *
         * UNPAID   -> Login blocked
         * INACTIVE -> Login blocked
         */
        if (user.getStatus() == UserStatus.UNPAID
                || user.getStatus() == UserStatus.INACTIVE) {

            throw new InactiveAccountException(
                    "Your account is inactive or your subscription has expired. Please contact the administrator."
            );
        }

        // Generate JWT
        String token = jwtUtil.generateToken(user);

        return new AuthResponse(
                token,
                user.getRoles().name()
        );
    }

    private AuthResponse authenticateUser(
            User user,
            String rawPassword
    ) {

        if (!passwordEncoder.matches(
                rawPassword,
                user.getPassword()
        )) {

            throw new RuntimeException(
                    "Authentication failed: Bad credentials for user"
            );
        }

        String token = jwtUtil.generateToken(user);

        return new AuthResponse(
                token,
                user.getRoles().name()
        );
    }
}