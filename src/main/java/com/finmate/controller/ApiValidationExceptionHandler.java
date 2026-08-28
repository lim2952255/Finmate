package com.finmate.controller;

import com.finmate.exception.BusinessRuleException;
import com.finmate.exception.DuplicatedId;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiValidationExceptionHandler {

    @ExceptionHandler(DuplicatedId.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicatedId exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("DUPLICATE_RESOURCE", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BUSINESS_RULE_VIOLATION", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        String message = fieldErrors.values().stream()
                .findFirst()
                .orElse("입력값을 확인해주세요.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("VALIDATION_FAILED", message, fieldErrors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REQUEST", "요청값의 형식을 확인해주세요.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "INVALID_PARAMETER",
                        exception.getName() + " 값의 형식을 확인해주세요.",
                        Map.of(exception.getName(), "올바른 형식의 값을 입력해주세요.")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("VALIDATION_FAILED", "입력값을 확인해주세요.", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "요청을 처리할 수 없습니다." : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiErrorResponse("REQUEST_REJECTED", message, Map.of()));
    }
  
    // 오류 응답용 DTO
    public record ApiErrorResponse(String code, String message, Map<String, String> fieldErrors) {
    }
}
