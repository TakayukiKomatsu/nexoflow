package com.srm.creditengine.shared.api;

import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import io.micrometer.core.instrument.Metrics;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import com.srm.creditengine.currency.domain.FxRateMissingException;
import com.srm.creditengine.currency.domain.FxRateStaleException;
import com.srm.creditengine.currency.domain.UnsupportedCurrencyException;
import com.srm.creditengine.currency.application.FxProviderUnavailableException;
import com.srm.creditengine.settlement.domain.AlreadySettledException;
import com.srm.creditengine.settlement.application.IdempotencyKeyReusedException;
import com.srm.creditengine.settlement.domain.AlreadyReversedException;
import com.srm.creditengine.settlement.domain.PricingQuoteExpiredException;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;

@RestControllerAdvice
public class ApiExceptionHandler {
    private FinancialTelemetry telemetry = new FinancialTelemetry(Metrics.globalRegistry);
    private SafeOperationalLogger operationalLogger = new SafeOperationalLogger();

    ApiExceptionHandler() {
    }

    @Autowired(required = false)
    void configureObservability(FinancialTelemetry telemetry, SafeOperationalLogger operationalLogger) {
        this.telemetry = telemetry;
        this.operationalLogger = operationalLogger;
    }
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), request);
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ProblemDetail loginRateLimited(LoginRateLimitedException exception, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", exception.getMessage(), request);
    }

    @ExceptionHandler(FxProviderUnavailableException.class)
    ProblemDetail fxProviderUnavailable(FxProviderUnavailableException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "FX_PROVIDER_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    ProblemDetail unsupportedCurrency(UnsupportedCurrencyException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", exception.getMessage(), request);
    }

    @ExceptionHandler(FxRateMissingException.class)
    ProblemDetail fxRateMissing(FxRateMissingException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "FX_RATE_MISSING", exception.getMessage(), request);
    }

    @ExceptionHandler(FxRateStaleException.class)
    ProblemDetail fxRateStale(FxRateStaleException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "FX_RATE_STALE", exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException exception, HttpServletRequest request) {
        telemetry.settlement("UNKNOWN", "conflict");
        operationalLogger.financialConflict();
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage(), request);
    }

    @ExceptionHandler(AlreadySettledException.class)
    ProblemDetail alreadySettled(AlreadySettledException exception, HttpServletRequest request) {
        telemetry.settlement(exception.settlementCurrency(), "conflict");
        operationalLogger.financialConflict();
        return problem(HttpStatus.CONFLICT, "ALREADY_SETTLED", exception.getMessage(), request);
    }
    @ExceptionHandler(AlreadyReversedException.class)
    ProblemDetail alreadyReversed(AlreadyReversedException exception, HttpServletRequest request) {
        telemetry.reversal("conflict");
        operationalLogger.financialConflict();
        return problem(HttpStatus.CONFLICT, "ALREADY_REVERSED", exception.getMessage(), request);
    }

    @ExceptionHandler(PricingQuoteExpiredException.class)
    ProblemDetail pricingQuoteExpired(
            PricingQuoteExpiredException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "PRICING_QUOTE_EXPIRED", exception.getMessage(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        boolean idempotencyKey = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName());
        return problem(
                HttpStatus.BAD_REQUEST,
                idempotencyKey ? "IDEMPOTENCY_KEY_REQUIRED" : "VALIDATION_FAILED",
                idempotencyKey ? "Idempotency-Key header is required." : "Request validation failed.",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        ProblemDetail detail =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request);
        detail.setTitle("Validation failed");
        detail.setProperty(
                "violations",
                List.of(Map.of(
                        "field", "request",
                        "message", "is malformed or contains an invalid value")));
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(DomainResourceNotFoundException.class)
    ProblemDetail domainResourceNotFound(
            DomainResourceNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The requested domain resource was not found.",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "The request conflicts with existing data.",
                request);
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
