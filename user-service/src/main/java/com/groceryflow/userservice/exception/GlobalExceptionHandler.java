package com.groceryflow.userservice.exception;

import com.groceryflow.userservice.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// ═══════════════════════════════════════════════════════════
// GlobalExceptionHandler: bắt exception từ tất cả controllers
//
// Tại sao cần?
//   - Không có handler → Spring trả HTML error page hoặc Spring's default JSON
//     (format khác ApiResponse → client khó parse)
//   - @RestControllerAdvice giúp trả ApiResponse<null> với message mô tả lỗi
//
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// ═══════════════════════════════════════════════════════════
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bean Validation fail (@NotBlank, @Size, ...) → 400 với danh sách lỗi
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    // Business logic error (username taken, invalid credentials, ...) → 400
    // Tại sao dùng IllegalArgumentException cho business errors?
    //   - RuntimeException quá generic (cũng catch programming bugs)
    //   - IllegalArgumentException semantically nghĩa là "input không hợp lệ"
    //   - AuthService ném IllegalArgumentException cho business validation
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    // Catch-all cho unexpected errors → 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
