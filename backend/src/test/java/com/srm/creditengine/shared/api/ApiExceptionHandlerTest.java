package com.srm.creditengine.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class ApiExceptionHandlerTest {

    @Test
    void usesASafeFallbackWhenBeanValidationProvidesNoMessage() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request", "amount", null, false, null, null, null));
        Method method = ValidationTarget.class.getDeclaredMethod("validate", Object.class);
        var exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), bindingResult);

        ProblemDetail detail =
                new ApiExceptionHandler().validationFailure(exception, request());

        assertThat(violations(detail))
                .containsExactly(Map.of("field", "amount", "message", "is invalid"));
    }

    @Test
    @SuppressWarnings("removal")
    void usesRequestLevelFallbacksWhenMethodValidationMetadataIsAbsent() throws Exception {
        var target = new ValidationTarget();
        Method method = ValidationTarget.class.getDeclaredMethod("validate", Object.class);
        var parameter = new MethodParameter(method, 0);
        var error = new DefaultMessageSourceResolvable(
                new String[] {"validation.failure"}, new Object[0], null);
        var parameterResult = new ParameterValidationResult(
                parameter, "invalid", List.of(error), null, null, null);
        var validationResult =
                MethodValidationResult.create(target, method, List.of(parameterResult));
        var exception = new HandlerMethodValidationException(validationResult);

        ProblemDetail detail =
                new ApiExceptionHandler().validationFailure(exception, request());

        assertThat(violations(detail))
                .containsExactly(Map.of("field", "request", "message", "is invalid"));
    }

    @Test
    void usesASafeRequestLevelViolationForAnUnknownValidationException() {
        ProblemDetail detail =
                new ApiExceptionHandler().validationFailure(new Exception("unsafe"), request());

        assertThat(violations(detail))
                .containsExactly(Map.of("field", "request", "message", "is invalid"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> violations(ProblemDetail detail) {
        return (List<Map<String, String>>) detail.getProperties().get("violations");
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/v1/test-validation");
        request.setAttribute(CorrelationIdFilter.ATTRIBUTE, "validation-test-001");
        return request;
    }

    private static final class ValidationTarget {
        @SuppressWarnings("unused")
        void validate(Object value) {
        }
    }
}
