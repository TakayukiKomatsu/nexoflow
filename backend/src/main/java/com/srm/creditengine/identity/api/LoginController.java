package com.srm.creditengine.identity.api;

import com.srm.creditengine.identity.application.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class LoginController {
    private final AuthenticationService authentication;

    LoginController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/login")
    AuthenticationService.AccessToken login(@Valid @RequestBody LoginRequest request) {
        return authentication.authenticate(request.email(), request.password());
    }

    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
}
