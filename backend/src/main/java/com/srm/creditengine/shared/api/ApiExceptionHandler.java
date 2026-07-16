package com.srm.creditengine.shared.api;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class
    })
    ProblemDetail validationFailure(Exception exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        detail.setType(URI.create("urn:srm:error:validation-failed"));
        detail.setTitle("Validation failed");
        detail.setProperty("code", "VALIDATION_FAILED");
        detail.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return detail;
    }
}
