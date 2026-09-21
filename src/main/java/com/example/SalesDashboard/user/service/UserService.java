package com.example.SalesDashboard.user.service;

import com.example.SalesDashboard.framework.model.UserRoles;
import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.repository.BusinessOrganizationRepository;
import com.example.SalesDashboard.user.command.OwnerCreateRequest;
import com.example.SalesDashboard.user.command.ResetPasswordCommand;
import com.example.SalesDashboard.user.command.UserRegisterCommand;
import com.example.SalesDashboard.user.command.UserUpdateCommand;
import com.example.SalesDashboard.user.dto.UserResponseDTO;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import com.example.SalesDashboard.user.exception.InvalidFormatPasswordException;
import com.example.SalesDashboard.user.exception.UserAlreadyExistException;
import com.example.SalesDashboard.user.repository.UserRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.example.SalesDashboard.subscription.entity.Subscription;
import com.example.SalesDashboard.subscription.repository.SubscriptionRepository;
import com.example.SalesDashboard.user.command.EmployeeCreateRequest;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BusinessOrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final SubscriptionRepository subscriptionRepository;


    // =========================================================
    // REGISTER USER
    // =========================================================

    public UserRegisterCommand registerUser(
            UserRegisterCommand request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistException(
                    "Email already in use: " + request.getEmail()
            );
        }

        if (request.getPassword() == null
                || request.getPassword().length() <= 5) {

            throw new InvalidFormatPasswordException(
                    "Password must be at least 6 characters long"
            );
        }

        Date now = new Date();

        User newUser = User.builder()

                // Generate UUID automatically
                .id(UUID.randomUUID().toString())

                .email(request.getEmail())

                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )

                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .address(request.getAddress())

                // Normal public user
                .roles(UserRoles.USER)

                // Start 15-day trial
                .status(UserStatus.TRIAL)

                /*
                 * Organization will be assigned later
                 * through the Owner / Employee flow.
                 */
                .organizationId(null)

                .createdOn(now)
                .createdAt(now)
                .statusUpdatedAt(now)

                .build();

        userRepository.save(newUser);


        return UserRegisterCommand.builder()

                .email(newUser.getEmail())
                .firstName(newUser.getFirstName())
                .lastName(newUser.getLastName())
                .mobile(newUser.getMobile())
                .address(newUser.getAddress())

                // Never return password
                .password(null)

                .message(
                        "User registered successfully! "
                                + "Your 15-day free trial has started."
                )

                .createdOn(newUser.getCreatedOn())

                .build();
    }

public UserResponseDTO createOwner(OwnerCreateRequest request) {

    // -----------------------------------------------------
    // Validate email
    // -----------------------------------------------------

    if (request.getEmail() == null
            || request.getEmail().isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Email is required"
        );
    }


    // -----------------------------------------------------
    // Check duplicate email
    // -----------------------------------------------------

    if (userRepository.existsByEmail(request.getEmail())) {

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email already in use: "
                        + request.getEmail()
        );
    }


    // -----------------------------------------------------
    // Validate password
    // -----------------------------------------------------

    if (request.getPassword() == null
            || request.getPassword().length() < 6) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Password must be at least 6 characters long"
        );
    }


    // -----------------------------------------------------
    // Validate organization ID
    // -----------------------------------------------------

    if (request.getOrganizationId() == null
            || request.getOrganizationId().isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Organization ID is required"
        );
    }


    // -----------------------------------------------------
    // Check organization exists
    // -----------------------------------------------------

    BusinessOrganization organization =
            organizationRepository.findById(
                    request.getOrganizationId()
            ).orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Organization not found: "
                                    + request.getOrganizationId()
                    )
            );


    // -----------------------------------------------------
    // Create Owner
    // -----------------------------------------------------

    Date now = new Date();

    User owner = User.builder()

            // Generate Owner UUID
            .id(UUID.randomUUID().toString())

            .email(request.getEmail())

            .password(
                    passwordEncoder.encode(
                            request.getPassword()
                    )
            )

            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .mobile(request.getMobile())
            .address(request.getAddress())

            // Backend controls the role
            .roles(UserRoles.OWNER)

            // Owner starts with trial
            .status(UserStatus.TRIAL)

            // Organization mapping
            .organizationId(organization.getId())

            .createdOn(now)
            .createdAt(now)
            .statusUpdatedAt(now)

            .build();


    // -----------------------------------------------------
    // Save Owner
    // -----------------------------------------------------

    User savedOwner = userRepository.save(owner);


    // -----------------------------------------------------
    // Return safe response
    // Password is NOT returned
    // -----------------------------------------------------

    return new UserResponseDTO(savedOwner);
}


    // =========================================================
    // UPDATE USER
    // =========================================================

    public UserResponseDTO updateUserRequiredFields(
            String id,
            UserUpdateCommand command
    ) {

        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );


        Map<String, Object> updates =
                objectMapper.convertValue(
                        command,
                        new TypeReference<>() {
                        }
                );


        updates.entrySet().removeIf(
                entry ->
                        entry.getValue() == null
                                || entry.getKey()
                                .startsWith("additionalProp")
        );


        if (updates.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No valid fields provided for update"
            );
        }


        updates.forEach((key, value) -> {

            switch (key) {

                // =====================================================
                // FIRST NAME
                // =====================================================

                case "firstName" ->
                        user.setFirstName(
                                (String) value
                        );


                // =====================================================
                // LAST NAME
                // =====================================================

                case "lastName" ->
                        user.setLastName(
                                (String) value
                        );


                // =====================================================
                // MOBILE
                // =====================================================

                case "mobile" ->
                        user.setMobile(
                                (String) value
                        );


                // =====================================================
                // ADDRESS
                // =====================================================

                case "address" ->
                        user.setAddress(
                                (String) value
                        );


                // =====================================================
                // ORGANIZATION ID
                // =====================================================

                case "organizationId" ->
                        user.setOrganizationId(
                                (String) value
                        );


                // =====================================================
                // ROLE
                // =====================================================

                case "roles" -> {

                    try {

                        user.setRoles(
                                UserRoles.valueOf(
                                        value
                                                .toString()
                                                .toUpperCase()
                                )
                        );

                    } catch (IllegalArgumentException e) {

                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid role value"
                        );
                    }
                }


                // =====================================================
                // STATUS
                // =====================================================

                case "status" -> {

                    try {

                        UserStatus newStatus =
                                UserStatus.valueOf(
                                        value
                                                .toString()
                                                .toUpperCase()
                                );


                        /*
                         * Update statusUpdatedAt ONLY when
                         * the status actually changes.
                         *
                         * Example:
                         *
                         * UNPAID -> PAID
                         *
                         * statusUpdatedAt becomes the
                         * payment date/time.
                         */

                        if (user.getStatus() != newStatus) {

                            user.setStatus(
                                    newStatus
                            );

                            user.setStatusUpdatedAt(
                                    new Date()
                            );
                        }

                    } catch (IllegalArgumentException e) {

                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid status value"
                        );
                    }
                }


                // =====================================================
                // INVALID FIELD
                // =====================================================

                default ->
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid field: " + key
                        );
            }
        });


        User savedUser =
                userRepository.save(user);


        return new UserResponseDTO(savedUser);
    }


    // =========================================================
    // FIND USER BY ID
    // =========================================================

    public User findUserById(String id) {

        return userRepository.findUserById(id);
    }


    // =========================================================
    // GET ALL USERS
    // =========================================================

    public List<User> lookUpAllUsers() {

        return userRepository.findAllUsers();
    }


    // =========================================================
    // GET ACTIVE USERS
    // =========================================================

    public List<User> getAllActiveUsers() {

        return userRepository.findAllUsersByStatus(
                List.of(UserStatus.ACTIVE)
        );
    }


    // =========================================================
    // GET USERS BY STATUS
    // =========================================================

    public List<User> getUsersByStatus(
            List<UserStatus> statuses
    ) {

        return userRepository.findAllUsersByStatus(
                statuses
        );
    }


    // =========================================================
    // PASSWORD RESET CODE
    // =========================================================

    public String generateResetCodeAndSendToEmail(
            String email
    ) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No user found with this email"
                                )
                        );


        String resetCode =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8);


        user.setPassword(
                passwordEncoder.encode(resetCode)
        );


        userRepository.save(user);


        sendPasswordResetEmail(
                email,
                resetCode
        );


        return "A reset code has been sent to your email.";
    }


    // =========================================================
    // SEND PASSWORD RESET EMAIL
    // =========================================================

    private void sendPasswordResetEmail(
            String email,
            String resetCode
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();


        message.setTo(email);

        message.setSubject(
                "Password Reset - Reset Code"
        );

        message.setText(
                "Your reset code is: "
                        + resetCode
                        + "\nPlease log in and change it immediately."
        );


        mailSender.send(message);
    }


    // =========================================================
    // RESET PASSWORD
    // =========================================================

    public String resetPasswordAfterVerification(
            ResetPasswordCommand request
    ) {

        User user =
                userRepository.findByEmail(
                        request.getEmail()
                )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No user found with this email"
                                )
                        );


        if (!passwordEncoder.matches(
                request.getResetCode(),
                user.getPassword()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Reset code is invalid."
            );
        }


        if (request.getNewPassword() == null
                || request.getNewPassword().length() < 6) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be at least 6 characters long."
            );
        }


        if (!request.getNewPassword().equals(
                request.getConfirmPassword()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password and confirm password do not match."
            );
        }


        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );


        userRepository.save(user);


        return "Password has been successfully reset.";
    }


    // =========================================================
    // FIND ALL USERS BY ID
    // =========================================================

    public Object findAllUsersById(String id) {

        User user =
                userRepository.findUserById(id);


        if (user != null) {

            return user;
        }


        throw new RuntimeException(
                "User not found"
        );
    }

    public UserResponseDTO createEmployee(
        String ownerUserId,
        EmployeeCreateRequest request
) {

    // -----------------------------------------------------
    // Find authenticated Owner
    // -----------------------------------------------------

    User owner = userRepository.findById(ownerUserId)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Owner not found"
                    )
            );


    // -----------------------------------------------------
    // Verify role
    // -----------------------------------------------------

    if (owner.getRoles() != UserRoles.OWNER) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only an Owner can create employees"
        );
    }


    // -----------------------------------------------------
    // Verify organization
    // -----------------------------------------------------

    String organizationId = owner.getOrganizationId();

    if (organizationId == null || organizationId.isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Owner is not associated with an organization"
        );
    }


    // -----------------------------------------------------
    // Verify Owner account status
    // -----------------------------------------------------

    if (owner.getStatus() == UserStatus.UNPAID
            || owner.getStatus() == UserStatus.INACTIVE) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your subscription is inactive or expired"
        );
    }


    // -----------------------------------------------------
    // Check duplicate email
    // -----------------------------------------------------

    if (userRepository.existsByEmail(request.getEmail())) {

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email already in use: " + request.getEmail()
        );
    }


    // -----------------------------------------------------
    // Find Organization
    // -----------------------------------------------------

    BusinessOrganization organization =
            organizationRepository.findById(organizationId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Organization not found"
                            )
                    );


    // -----------------------------------------------------
    // Verify subscription
    // -----------------------------------------------------

    if (organization.getSubscriptionId() == null
            || organization.getSubscriptionId().isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No subscription is assigned to this organization"
        );
    }


    Subscription subscription =
            subscriptionRepository.findById(
                    organization.getSubscriptionId()
            ).orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Subscription not found"
                    )
            );


    // -----------------------------------------------------
    // Verify subscription status
    // -----------------------------------------------------

    String subscriptionStatus = subscription.getStatus();

    if (subscriptionStatus == null
            || subscriptionStatus.isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Subscription status is not configured"
        );
    }


    String normalizedStatus =
            subscriptionStatus.trim().toUpperCase();


    /*
     * Your current system uses subscription status as String.
     *
     * Allow:
     * TRIAL
     * ACTIVE
     * PAID
     *
     * Block everything else.
     */

    if (!normalizedStatus.equals("TRIAL")
            && !normalizedStatus.equals("ACTIVE")
            && !normalizedStatus.equals("PAID")) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your subscription is not active"
        );
    }


    // -----------------------------------------------------
    // Validate max users
    // -----------------------------------------------------

    if (subscription.getMaxUsers() == null
            || subscription.getMaxUsers() <= 0) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Invalid user limit configured for this subscription"
        );
    }


    // -----------------------------------------------------
    // Count current organization users
    //
    // IMPORTANT:
    // This includes Owner + Employees.
    // -----------------------------------------------------

    long currentUsers =
            userRepository.countByOrganizationId(
                    organizationId
            );


    // -----------------------------------------------------
    // Check subscription limit
    // -----------------------------------------------------

    if (currentUsers >= subscription.getMaxUsers()) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "User limit reached for your subscription. "
                        + "Maximum allowed users: "
                        + subscription.getMaxUsers()
        );
    }


    // -----------------------------------------------------
    // Create Employee
    // -----------------------------------------------------

    Date now = new Date();

    User employee = User.builder()

            .id(UUID.randomUUID().toString())

            .email(request.getEmail())

            .password(
                    passwordEncoder.encode(
                            request.getPassword()
                    )
            )

            .firstName(request.getFirstName())

            .lastName(request.getLastName())

            .mobile(request.getMobile())

            .address(request.getAddress())

            // Backend controls role
            .roles(UserRoles.EMPLOYEE)

            // Employee follows organization lifecycle
            .status(owner.getStatus())

            // IMPORTANT:
            // Automatically inherit Owner organization
            .organizationId(organizationId)

            .createdOn(now)

            .createdAt(now)

            .statusUpdatedAt(now)

            .build();


    // -----------------------------------------------------
    // Save Employee
    // -----------------------------------------------------

    User savedEmployee =
            userRepository.save(employee);


    // -----------------------------------------------------
    // Return safe response
    // -----------------------------------------------------

    return new UserResponseDTO(savedEmployee);
}
}