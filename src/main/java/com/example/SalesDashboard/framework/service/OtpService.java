package com.example.SalesDashboard.framework.service;

import com.example.SalesDashboard.framework.security.JwtUtil;
import com.example.SalesDashboard.otp.entity.Otp;
import com.example.SalesDashboard.otp.repository.OtpRepository;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final JwtUtil jwtUtil;
    private final Random random = new SecureRandom();

    public OtpService(UserRepository userRepository,
                      JavaMailSender mailSender,
                      JwtUtil jwtUtil
    ) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.jwtUtil = jwtUtil;
    }

    public ResponseEntity<String> generateOtpForEmail(String email) {
        boolean emailExists =
                userRepository.findByEmail(email).isPresent();
        if (emailExists) {
            String otp = generateRandomOtp();

            Otp otpEntry = new Otp();
            otpEntry.setEmail(email);
            otpEntry.setOtp(otp);
            otpRepository.save(otpEntry);

            sendOtpEmail(email, otp);
            return ResponseEntity.ok("OTP has been sent to your email.");
        } else {
            throw new RuntimeException("Error: Email not found.");
        }
    }

    public String validateOtp(String email, String otp) {
        List<Otp> otpEntries = otpRepository.findByEmail(email);
        if (otpEntries.isEmpty()) return null;

        Otp latestOtp = otpEntries.getLast();
        boolean isValid = otp.equals(latestOtp.getOtp());

        if (isValid) {
            otpRepository.deleteAll(otpEntries);

            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isPresent()) {
                return jwtUtil.generateToken(optionalUser.get());
            }
        }
        return null;
    }

    private String generateRandomOtp() {
        return String.format("%06d", random.nextInt(1000000));
    }

    private void sendOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP is: " + otp);
        mailSender.send(message);
    }
}
