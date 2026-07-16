package com.srm.creditengine.identity.api;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class LoginController {
    @PostMapping("/login")
    ProblemDetail login(@Valid @RequestBody LoginRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        detail.setType(URI.create("urn:srm:error:invalid-credentials"));
        detail.setTitle("Unauthorized");
        detail.setProperty("code", "INVALID_CREDENTIALS");
        return detail;
    }

    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }
}
