package com.groceryflow.userservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;

    // expiresIn tính bằng ms — client tự tính thời điểm hết hạn
    // Tại sao không trả expiresAt (timestamp)?
    //   → expiresIn không phụ thuộc timezone của server hay client
    //   → Client lấy Date.now() + expiresIn là đủ
    private long expiresIn;

    // Trả thông tin user cơ bản — client không cần gọi thêm API /profile
    private UserResponse user;
}
