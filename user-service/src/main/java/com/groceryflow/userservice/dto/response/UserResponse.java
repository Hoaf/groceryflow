package com.groceryflow.userservice.dto.response;

import com.groceryflow.userservice.model.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private String id;
    private String username;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;
    // password không có ở đây — không bao giờ trả password ra ngoài dù đã hash
}
