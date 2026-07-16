package com.srm.creditengine.shared.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), request);
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ProblemDetail loginRateLimited(LoginRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "The request media type is not supported.",
                request);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class
    })
    ProblemDetail validationFailure(Exception exception, HttpServletRequest request) {
        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request);
        detail.setTitle("Validation failed");
        detail.setProperty("violations", violations(exception));
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("urn:srm:error:" + code.toLowerCase().replace('_', '-')));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        detail.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return detail;
    }

    private List<Map<String, String>> violations(Exception exception) {
        if (exception instanceof MissingServletRequestParameterException missing) {
            return List.of(Map.of("field", missing.getParameterName(), "message", "is required"));
        }
        if (exception instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                    .toList();
        }
        if (exception instanceof HandlerMethodValidationException invalid) {
            var result = new ArrayList<Map<String, String>>();
            invalid.getParameterValidationResults().forEach(validation -> {
                String field = validation.getMethodParameter().getParameterName();
                validation.getResolvableErrors().forEach(error -> result.add(Map.of(
                        "field", field == null ? "request" : field,
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage())));
            });
            return result;
        }
        return List.of(Map.of("field", "request", "message", "is invalid"));
    }
}
