package com.meterflow.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException e) {
    return ProblemDetail.forStatusAndDetail(
        e.getStatusCode(), e.getReason() == null ? "Request failed" : e.getReason());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ProblemDetail invalid(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid input. Check required fields, formats and limits.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "A conflicting record exists or a related resource is unavailable.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail denied() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.FORBIDDEN, "You do not have permission for this operation.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    // Exception messages may contain SQL parameters or credentials. Log class only.
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Request failed: {}", e.getClass().getSimpleName());
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Request failed. Try again later.");
  }
}
