package com.groceryflow.userservice.repository;

import com.groceryflow.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Dùng khi login — load full entity để check password
    Optional<User> findByUsername(String username);

    // Dùng khi register — check duplicate mà không cần load toàn bộ entity
    boolean existsByUsername(String username);
}
