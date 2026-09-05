package com.example.SalesDashboard.user.repository;

import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.entity.UserStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    @Aggregation({
            "{ $match: { _id: :#{#id} } }",
            "{ $lookup: { " +
                    "from: 'subscription', " +
                    "localField: 'subscriptionId', " +
                    "foreignField: '_id', " +
                    "as: 'subscriptionInfo' } }",
            "{ $unwind: { " +
                    "path: '$subscriptionInfo', " +
                    "preserveNullAndEmptyArrays: true } }"
    })
    User findUserById(String id);

    @Aggregation({
            "{ $lookup: { " +
                    "from: 'subscription', " +
                    "localField: 'subscriptionId', " +
                    "foreignField: '_id', " +
                    "as: 'subscriptionInfo' } }",
            "{ $unwind: { " +
                    "path: '$subscriptionInfo', " +
                    "preserveNullAndEmptyArrays: true } }"
    })
    List<User> findAllUsers();

    @Aggregation({
            "{ $match: { 'status': { $in: :#{#status} } } }",
            "{ $lookup: { " +
                    "from: 'subscription', " +
                    "localField: 'subscriptionId', " +
                    "foreignField: '_id', " +
                    "as: 'subscriptionInfo' } }",
            "{ $unwind: { " +
                    "path: '$subscriptionInfo', " +
                    "preserveNullAndEmptyArrays: true } }"
    })
    List<User> findAllUsersByStatus(List<UserStatus> status);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);


}
