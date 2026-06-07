package com.LastBite.common.exception;

import com.LastBite.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ xử lý exception toàn cục — bắt exception và trả về cùng một dạng
 * {@link ApiResponse}.
 * <p>
 * Điểm cải thiện:
 * <ul>
 *   <li>Một class duy nhất, không cần {@code ErrorResponse} dư thừa</li>
 *   <li>Dùng switch expression rõ ràng để map HTTP status</li>
 *   <li>Áp dụng cho toàn bộ controller, không chỉ một package</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ── Business exception ─────────────────────────────────────────────
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(
            ApiException ex, HttpServletRequest request) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(error(ec.getCode(), ex.getMessage(), request.getRequestURI(), null));
    }

    // ── @Validated on params / path variables ──────────────────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));

        ErrorCode ec = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(error(ec.getCode(), ec.getDefaultMessage(), request.getRequestURI(), errors));
    }

    // ── @Valid on @RequestBody DTOs ────────────────────────────────────
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }

        ErrorCode ec = ErrorCode.VALIDATION_ERROR;
        return new ResponseEntity<>(
                error(ec.getCode(), ec.getDefaultMessage(), extractPath(request), errors),
                ec.getStatus());
    }

    // ── Malformed JSON body ───────────────────────────────────────────
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ErrorCode ec = ErrorCode.MALFORMED_JSON;
        return new ResponseEntity<>(
                error(ec.getCode(), ec.getDefaultMessage(), extractPath(request), null),
                ec.getStatus());
    }

    // ── Spring Security: access denied ────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(ec.getStatus())
                .body(error(ec.getCode(), ec.getDefaultMessage(), request.getRequestURI(), null));
    }

    // ── DB constraint violation (unique, FK, etc.) ────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMessage());
        ErrorCode ec = ErrorCode.DUPLICATE_RESOURCE;
        return ResponseEntity.status(ec.getStatus())
                .body(error(ec.getCode(), ec.getDefaultMessage(), request.getRequestURI(), null));
    }

    // ── Catch-all ─────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi:", ex);
        ErrorCode ec = ErrorCode.UNEXPECTED_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(error(ec.getCode(), ec.getDefaultMessage(), request.getRequestURI(), null));
        }

    // ── Spring internal exceptions (405, 415, etc.) ───────────────────
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode ec = mapStatus(status);
        return new ResponseEntity<>(
                error(ec.getCode(), ec.getDefaultMessage(), extractPath(request), null),
                headers, status);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ApiResponse<Object> error(int code, String message, String path,
                                      Map<String, String> errors) {
        return ApiResponse.<Object>builder()
                .code(code)
                .message(message)
                .result(null)
                .errors(errors)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    private String extractPath(WebRequest request) {
        String desc = request.getDescription(false);
        return (desc != null && desc.startsWith("uri=")) ? desc.substring(4) : desc;
    }

    private ErrorCode mapStatus(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.REQUEST_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            default -> status.is4xxClientError()
                    ? ErrorCode.REQUEST_FAILED
                    : ErrorCode.UNEXPECTED_ERROR;
        };
    }
}
