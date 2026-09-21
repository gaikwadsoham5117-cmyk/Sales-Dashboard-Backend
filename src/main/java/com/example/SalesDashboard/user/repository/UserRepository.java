package com.example.SalesDashboard.user.repository;

import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import com.example.SalesDashboard.framework.model.UserRoles;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    User findUserById(String id);

    @Query("{}")
    List<User> findAllUsers();

    @Aggregation({
            "{ $match: { 'status': { $in: :#{#status} } } }"
    })
    List<User> findAllUsersByStatus(List<UserStatus> status);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /*
     * Finds users whose 15-day trial has expired.
     */
    List<User> findByStatusAndCreatedAtBefore(
            UserStatus status,
            Date date
    );

    /*
     * Finds PAID users whose one-year subscription has expired.
     */
    List<User> findByStatusAndStatusUpdatedAtBefore(
            UserStatus status,
            Date date
    );

    List<User> findByOrganizationId(String organizationId);

long countByOrganizationId(String organizationId);

List<User> findByOrganizationIdAndRoles(
        String organizationId,
        UserRoles roles
);
}