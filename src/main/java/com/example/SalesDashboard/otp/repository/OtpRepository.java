package com.example.SalesDashboard.otp.repository;

import com.example.SalesDashboard.otp.entity.Otp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OtpRepository extends MongoRepository<Otp, String> {
    List<Otp> findByEmail(String email);
    List<Otp> findByPhoneNumber(String phoneNumber);

    void deleteByEmail(String email);
}
