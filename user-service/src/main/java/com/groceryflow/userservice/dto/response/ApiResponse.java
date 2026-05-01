package com.groceryflow.userservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// Generic wrapper cho mọi API response
// Tại sao wrap response?
//   - Client luôn biết cấu trúc response → không cần check HTTP status để parse
//   - Dễ thêm metadata (timestamp, traceId) về sau mà không break client
//   - success=false + message giải thích lỗi rõ ràng hơn chỉ dùng HTTP status code
@Data
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    // Factory method — tránh gọi builder verbose ở mọi nơi
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
