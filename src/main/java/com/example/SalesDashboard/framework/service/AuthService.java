package com.example.SalesDashboard.framework.service;

import com.example.SalesDashboard.framework.dto.AuthRequest;
import com.example.SalesDashboard.framework.dto.AuthResponse;
import com.example.SalesDashboard.framework.security.JwtUtil;

import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.entity.SubscriptionStatus;
import com.example.SalesDashboard.organization.repository.BusinessOrganizationRepository;

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

    private final BusinessOrganizationRepository organizationRepository;

    private final PasswordEncoder passwordEncoder;


    // =========================================================
    // REGISTER USER
    // =========================================================

    public UserRegisterCommand registerUser(
            UserRegisterCommand command
    ) {

        return userService.registerUser(command);
    }


    // =========================================================
    // LOGIN
    // =========================================================

    public AuthResponse login(
            AuthRequest request
    ) {

        // -----------------------------------------------------
        // Find User
        // -----------------------------------------------------

        User user =
                userRepository.findByEmail(
                                request.getEmail()
                        )
                        .orElseThrow(() ->
                                new InvalidCredentialsException(
                                        "Invalid email or password"
                                )
                        );


        // -----------------------------------------------------
        // Validate Password
        // -----------------------------------------------------

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {

            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }


        // -----------------------------------------------------
        // USER STATUS CHECK
        //
        // This only checks whether this particular
        // user is enabled or disabled.
        // -----------------------------------------------------

        if (user.getStatus() == UserStatus.INACTIVE) {

            throw new InactiveAccountException(
                    "Your account is disabled. Please contact your administrator."
            );
        }


        // -----------------------------------------------------
        // USER MUST BELONG TO ORGANIZATION
        // -----------------------------------------------------

        if (user.getOrganizationId() == null
                || user.getOrganizationId().isBlank()) {

            /*
             * If you still support normal users without
             * organization, you can handle that separately.
             *
             * For Owner/Employee organization login this
             * should always exist.
             */

            if (user.getRoles() != null
                    && (user.getRoles().name().equals("OWNER")
                    || user.getRoles().name().equals("EMPLOYEE"))) {

                throw new InactiveAccountException(
                        "Your account is not associated with an organization."
                );
            }

        } else {

            // -------------------------------------------------
            // FIND ORGANIZATION
            // -------------------------------------------------

            BusinessOrganization organization =
                    organizationRepository.findById(
                                    user.getOrganizationId()
                            )
                            .orElseThrow(() ->
                                    new InactiveAccountException(
                                            "Your organization could not be found."
                                    )
                            );


            // -------------------------------------------------
            // CHECK ORGANIZATION SUBSCRIPTION
            // -------------------------------------------------

            SubscriptionStatus subscriptionStatus =
                    organization.getSubscriptionStatus();


            if (subscriptionStatus == null) {

                throw new InactiveAccountException(
                        "Your organization's subscription status is not configured."
                );
            }


            // -------------------------------------------------
            // ALLOWED SUBSCRIPTIONS
            //
            // TRIAL -> allowed
            // PAID  -> allowed
            // UNPAID -> blocked
            // -------------------------------------------------

            if (subscriptionStatus == SubscriptionStatus.UNPAID) {

                throw new InactiveAccountException(
                        "Your organization's subscription has expired. Please contact the administrator."
                );
            }
        }


        // -----------------------------------------------------
        // GENERATE JWT
        // -----------------------------------------------------

        String token =
                jwtUtil.generateToken(user);


        return new AuthResponse(
                token,
                user.getRoles().name()
        );
    }


    // =========================================================
    // INTERNAL AUTHENTICATION
    // =========================================================

    private AuthResponse authenticateUser(
            User user,
            String rawPassword
    ) {

        if (!passwordEncoder.matches(
                rawPassword,
                user.getPassword()
        )) {

            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }


        String token =
                jwtUtil.generateToken(user);


        return new AuthResponse(
                token,
                user.getRoles().name()
        );
    }
}