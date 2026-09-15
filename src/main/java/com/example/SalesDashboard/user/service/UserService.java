package com.example.SalesDashboard.user.service;

import com.example.SalesDashboard.framework.model.UserRoles;
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

    private final PasswordEncoder passwordEncoder;

    private final JavaMailSender mailSender;

    private final ObjectMapper objectMapper;


    // =========================================================
    // REGISTER USER
    // =========================================================

    public UserRegisterCommand registerUser(UserRegisterCommand request) {

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

                .roles(UserRoles.USER)

                /*
                 * NEW SUBSCRIPTION FLOW
                 *
                 * Every newly registered user gets
                 * 15 days free trial.
                 */
                .status(UserStatus.TRIAL)

                /*
                 * Existing registration date.
                 */
                .createdOn(now)

                /*
                 * Subscription/trial start date.
                 */
                .createdAt(now)

                /*
                 * Initial status update time.
                 */
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
                        "User registered successfully! "
                                + "Your 15-day free trial has started."
                )

                .createdOn(newUser.getCreatedOn())

                .build();
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
                         * statusUpdatedAt becomes the payment
                         * date/time.
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
}