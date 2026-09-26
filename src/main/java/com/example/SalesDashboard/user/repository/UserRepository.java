package com.example.SalesDashboard.user.repository;

import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    User findUserById(String id);

    @Query("{}")
    List<User> findAllUsers();

    List<User> findAllUsersByStatus(
            List<UserStatus> statuses
    );

    long countByOrganizationId(
            String organizationId
    );

    List<User> findByOrganizationId(
            String organizationId
    );

    List<User> findByOrganizationIdAndRoles(
            String organizationId,
            com.example.SalesDashboard.framework.model.UserRoles roles
    );
}