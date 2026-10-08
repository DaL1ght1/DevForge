package com.example.devforge.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {

    log.warn("Resource not found at path [{}]: {}", request.getRequestURI(), ex.getMessage());

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Resource Not Found",
            ex.getMessage(),
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    Map<String, String> errors = new HashMap<>();

    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));

    log.warn("Validation failed for request to [{}]: {}", request.getRequestURI(), errors);

    ApiErrorResponse error =
        new ApiErrorResponse(
            LocalDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "One or more fields failed validation",
            request.getRequestURI(),
            errors);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<ApiErrorResponse> handleExists(
      UserAlreadyExistsException ex, HttpServletRequest request) {

    log.warn("User already exists at path [{}]: {}", request.getRequestURI(), ex.getMessage());

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage(), request.getRequestURI());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(RegistrationRejectedException.class)
  public ResponseEntity<ApiErrorResponse> handleRejected(
      RegistrationRejectedException ex, HttpServletRequest request) {

    log.warn("Registration rejected at path [{}]: {}", request.getRequestURI(), ex.getMessage());

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Registration Rejected",
            ex.getMessage(),
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {

    log.warn("Data integrity violation at path [{}]", request.getRequestURI(), ex);

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Data Integrity Violation",
            "Username or email already in use",
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("Invalid request at path [{}]: {}", request.getRequestURI(), ex.getMessage());
    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI());
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(IdentityProviderException.class)
  public ResponseEntity<ApiErrorResponse> handleIdp(
      IdentityProviderException ex, HttpServletRequest request) {

    log.error("Identity provider error at path [{}]", request.getRequestURI(), ex);

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.BAD_GATEWAY.value(),
            "Authentication Service Unavailable",
            "Authentication service unavailable",
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {

    log.warn("Invalid credentials attempt at [{}]: {}", request.getRequestURI(), ex.getMessage());

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            ex.getMessage(),
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
  }

  @ExceptionHandler(UnauthorizedAccessException.class)
  public ResponseEntity<ApiErrorResponse> handleUnauthorizedAccess(
      UnauthorizedAccessException ex, HttpServletRequest request) {

    log.warn("Unauthorized access attempt at [{}]: {}", request.getRequestURI(), ex.getMessage());

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI());

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGlobalException(
      Exception ex, HttpServletRequest request) {

    log.error("Unhandled exception occurred on path [{}]", request.getRequestURI(), ex);

    ApiErrorResponse error =
        new ApiErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
