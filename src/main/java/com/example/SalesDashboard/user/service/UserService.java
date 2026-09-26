package com.example.SalesDashboard.user.service;

import com.example.SalesDashboard.framework.model.UserRoles;
import com.example.SalesDashboard.organization.entity.BusinessOrganization;
import com.example.SalesDashboard.organization.entity.SubscriptionStatus;
import com.example.SalesDashboard.organization.repository.BusinessOrganizationRepository;

import com.example.SalesDashboard.subscription.entity.Subscription;
import com.example.SalesDashboard.subscription.repository.SubscriptionRepository;

import com.example.SalesDashboard.user.command.EmployeeCreateRequest;
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
            UserRegisterCommand request
    ) {

        if (userRepository.existsByEmail(
                request.getEmail()
        )) {

            throw new UserAlreadyExistException(
                    "Email already in use: "
                            + request.getEmail()
            );
        }

        if (request.getPassword() == null
                || request.getPassword().length() < 6) {

            throw new InvalidFormatPasswordException(
                    "Password must be at least 6 characters long"
            );
        }

        Date now = new Date();

        User newUser =
                User.builder()

                        .id(UUID.randomUUID().toString())

                        .email(request.getEmail())

                        .password(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )

                        .firstName(
                                request.getFirstName()
                        )

                        .lastName(
                                request.getLastName()
                        )

                        .mobile(
                                request.getMobile()
                        )

                        .address(
                                request.getAddress()
                        )

                        .roles(UserRoles.USER)

                        /*
                         * Standalone user account.
                         */
                        .status(UserStatus.ACTIVE)

                        /*
                         * No organization.
                         */
                        .organizationId(null)

                        /*
                         * User enabled.
                         */
                        .enabled(true)

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

                .password(null)

                .message(
                        "User registered successfully."
                )

                .createdOn(
                        newUser.getCreatedOn()
                )

                .build();
    }


    // =========================================================
    // CREATE OWNER
    // =========================================================

    public UserResponseDTO createOwner(
            OwnerCreateRequest request
    ) {

        if (request.getEmail() == null
                || request.getEmail().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required"
            );
        }

        if (userRepository.existsByEmail(
                request.getEmail()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already in use: "
                            + request.getEmail()
            );
        }

        if (request.getPassword() == null
                || request.getPassword().length() < 6) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 6 characters long"
            );
        }

        if (request.getOrganizationId() == null
                || request.getOrganizationId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization ID is required"
            );
        }

        BusinessOrganization organization =
                organizationRepository.findById(
                        request.getOrganizationId()
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Organization not found"
                        )
                );

        Date now = new Date();

        /*
         * If organization does not yet have
         * a subscription status, start it as TRIAL.
         */
        if (organization.getSubscriptionStatus() == null) {

            organization.setSubscriptionStatus(
                    SubscriptionStatus.TRIAL
            );

            organization.setSubscriptionStartedAt(now);

            organization.setSubscriptionUpdatedAt(
                    now
            );

            organizationRepository.save(
                    organization
            );
        }

        User owner =
                User.builder()

                        .id(
                                UUID.randomUUID()
                                        .toString()
                        )

                        .email(
                                request.getEmail()
                        )

                        .password(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )

                        .firstName(
                                request.getFirstName()
                        )

                        .lastName(
                                request.getLastName()
                        )

                        .mobile(
                                request.getMobile()
                        )

                        .address(
                                request.getAddress()
                        )

                        .roles(
                                UserRoles.OWNER
                        )

                        /*
                         * User account status.
                         *
                         * Subscription is NOT stored here.
                         */
                        .status(
                                UserStatus.ACTIVE
                        )

                        .organizationId(
                                organization.getId()
                        )

                        .enabled(true)

                        .createdOn(now)

                        .createdAt(now)

                        .statusUpdatedAt(now)

                        .build();

        User savedOwner =
                userRepository.save(owner);

        return new UserResponseDTO(
                savedOwner
        );
    }


    // =========================================================
    // UPDATE USER
    // =========================================================

    public UserResponseDTO updateUserRequiredFields(
            String id,
            UserUpdateCommand command
    ) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        Map<String, Object> updates =
                objectMapper.convertValue(
                        command,
                        new TypeReference<Map<String, Object>>() {}
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

                case "firstName" ->
                        user.setFirstName(
                                (String) value
                        );

                case "lastName" ->
                        user.setLastName(
                                (String) value
                        );

                case "mobile" ->
                        user.setMobile(
                                (String) value
                        );

                case "address" ->
                        user.setAddress(
                                (String) value
                        );

                case "organizationId" ->
                        user.setOrganizationId(
                                (String) value
                        );

                case "roles" -> {

                    try {

                        user.setRoles(
                                UserRoles.valueOf(
                                        value.toString()
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

                /*
                 * Only ACTIVE / INACTIVE now.
                 *
                 * Subscription is NOT changed here.
                 */
                case "status" -> {

                    try {

                        UserStatus newStatus =
                                UserStatus.valueOf(
                                        value.toString()
                                                .toUpperCase()
                                );

                        if (user.getStatus()
                                != newStatus) {

                            user.setStatus(
                                    newStatus
                            );

                            user.setStatusUpdatedAt(
                                    new Date()
                            );
                        }

                    } catch (
                            IllegalArgumentException e
                    ) {

                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid user status"
                        );
                    }
                }

                /*
                 * Do NOT allow organization subscription
                 * status to be changed through user update.
                 */
                case "enabled" -> {

                    if (value instanceof Boolean) {

                        user.setEnabled(
                                (Boolean) value
                        );

                    } else {

                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Enabled must be true or false"
                        );
                    }
                }

                default ->
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid field: " + key
                        );
            }
        });

        User savedUser =
                userRepository.save(user);

        return new UserResponseDTO(
                savedUser
        );
    }


    // =========================================================
    // CREATE EMPLOYEE
    // =========================================================

    public UserResponseDTO createEmployee(
            String ownerUserId,
            EmployeeCreateRequest request
    ) {

        User owner =
                getAndValidateOwner(
                        ownerUserId
                );

        String organizationId =
                owner.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Owner is not associated with an organization"
            );
        }

        BusinessOrganization organization =
                organizationRepository.findById(
                        organizationId
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Organization not found"
                        )
                );

        // -----------------------------------------------------
        // SUBSCRIPTION
        // -----------------------------------------------------

        SubscriptionStatus subscriptionStatus =
                organization.getSubscriptionStatus();

        if (subscriptionStatus == null) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization subscription status is not configured"
            );
        }

        if (subscriptionStatus
                == SubscriptionStatus.UNPAID) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your subscription is inactive or expired"
            );
        }

        // -----------------------------------------------------
        // SUBSCRIPTION DETAILS
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

        if (subscription.getMaxUsers() == null
                || subscription.getMaxUsers() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid user limit configured"
            );
        }

        long currentUsers =
                userRepository.countByOrganizationId(
                        organizationId
                );

        if (currentUsers
                >= subscription.getMaxUsers()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User limit reached. Maximum allowed users: "
                            + subscription.getMaxUsers()
            );
        }

        // -----------------------------------------------------
        // DUPLICATE EMAIL
        // -----------------------------------------------------

        if (userRepository.existsByEmail(
                request.getEmail()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already in use: "
                            + request.getEmail()
            );
        }

        // -----------------------------------------------------
        // PASSWORD
        // -----------------------------------------------------

        if (request.getPassword() == null
                || request.getPassword().length() < 6) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 6 characters long"
            );
        }

        // -----------------------------------------------------
        // CREATE EMPLOYEE
        // -----------------------------------------------------

        Date now = new Date();

        User employee =
                User.builder()

                        .id(
                                UUID.randomUUID()
                                        .toString()
                        )

                        .email(
                                request.getEmail()
                        )

                        .password(
                                passwordEncoder.encode(
                                        request.getPassword()
                                )
                        )

                        .firstName(
                                request.getFirstName()
                        )

                        .lastName(
                                request.getLastName()
                        )

                        .mobile(
                                request.getMobile()
                        )

                        .address(
                                request.getAddress()
                        )

                        .roles(
                                UserRoles.EMPLOYEE
                        )

                        /*
                         * Employee account status.
                         *
                         * NOT subscription status.
                         */
                        .status(
                                UserStatus.ACTIVE
                        )

                        .organizationId(
                                organizationId
                        )

                        .enabled(true)

                        .createdOn(now)

                        .createdAt(now)

                        .statusUpdatedAt(now)

                        .build();

        User savedEmployee =
                userRepository.save(
                        employee
                );

        return new UserResponseDTO(
                savedEmployee
        );
    }


    // =========================================================
    // CHANGE EMPLOYEE STATUS (single enable/disable API)
    //
    // enabled = true  -> UserStatus.ACTIVE
    // enabled = false -> UserStatus.INACTIVE
    //
    // This only ever touches the employee's own
    // User.status / User.enabled fields. It never
    // reads or writes the organization's
    // subscriptionStatus.
    // =========================================================

    public UserResponseDTO changeEmployeeStatus(
            String ownerUserId,
            String employeeId,
            Boolean enabled
    ) {

        if (enabled == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enabled must be true or false"
            );
        }

        User owner =
                getAndValidateOwner(
                        ownerUserId
                );

        User employee =
                getAndValidateEmployeeForOwner(
                        owner,
                        employeeId
                );

        employee.setEnabled(enabled);

        employee.setStatus(
                enabled
                        ? UserStatus.ACTIVE
                        : UserStatus.INACTIVE
        );

        employee.setStatusUpdatedAt(
                new Date()
        );

        User savedEmployee =
                userRepository.save(
                        employee
                );

        return new UserResponseDTO(
                savedEmployee
        );
    }

    // =========================================================
    // DELETE EMPLOYEE
    // =========================================================

    public void deleteEmployee(
            String ownerUserId,
            String employeeId
    ) {

        User owner =
                getAndValidateOwner(
                        ownerUserId
                );

        User employee =
                getAndValidateEmployeeForOwner(
                        owner,
                        employeeId
                );

        userRepository.delete(employee);
    }


    // =========================================================
    // ALL USERS FOR AN ORGANIZATION
    // =========================================================

    public List<User> findAllUsersById(
            String organizationId
    ) {

        return userRepository.findByOrganizationId(
                organizationId
        );
    }


    // =========================================================
    // VALIDATE OWNER
    // =========================================================

    private User getAndValidateOwner(
            String ownerUserId
    ) {

        User owner =
                userRepository.findById(
                        ownerUserId
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Owner not found"
                        )
                );

        if (owner.getRoles()
                != UserRoles.OWNER) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only an Owner can perform this action"
            );
        }

        if (owner.getEnabled() == null
                || !owner.getEnabled()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Owner account is disabled"
            );
        }

        if (owner.getStatus()
                == UserStatus.INACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Owner account is inactive"
            );
        }

        return owner;
    }


    // =========================================================
    // VALIDATE EMPLOYEE
    // =========================================================

    private User getAndValidateEmployeeForOwner(
            User owner,
            String employeeId
    ) {

        User employee =
                userRepository.findById(
                        employeeId
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Employee not found"
                        )
                );

        if (employee.getRoles()
                != UserRoles.EMPLOYEE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected user is not an employee"
            );
        }

        if (employee.getOrganizationId() == null
                || !employee.getOrganizationId()
                .equals(owner.getOrganizationId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot modify an employee from another organization"
            );
        }

        return employee;
    }


    // =========================================================
    // FIND USER
    // =========================================================

    public User findUserById(
            String id
    ) {

        return userRepository.findUserById(id);
    }


    // =========================================================
    // ALL USERS
    // =========================================================

    public List<User> lookUpAllUsers() {

        return userRepository.findAllUsers();
    }


    // =========================================================
    // ACTIVE USERS
    // =========================================================

    public List<User> getAllActiveUsers() {

        return userRepository.findAllUsersByStatus(
                List.of(UserStatus.ACTIVE)
        );
    }


    // =========================================================
    // USERS BY STATUS
    // =========================================================

    public List<User> getUsersByStatus(
            List<UserStatus> statuses
    ) {

        return userRepository.findAllUsersByStatus(
                statuses
        );
    }


    // =========================================================
    // PASSWORD RESET
    // =========================================================

    public String generateResetCodeAndSendToEmail(
            String email
    ) {

        User user =
                userRepository.findByEmail(
                        email
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No user found with this email"
                        )
                );

        String resetCode =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8);

        user.setPassword(
                passwordEncoder.encode(
                        resetCode
                )
        );

        userRepository.save(user);

        sendPasswordResetEmail(
                email,
                resetCode
        );

        return "A reset code has been sent to your email.";
    }


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
                ).orElseThrow(() ->
                        new ResponseStatusException(
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

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

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
}