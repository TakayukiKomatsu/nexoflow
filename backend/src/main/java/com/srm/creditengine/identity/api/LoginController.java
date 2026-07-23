package com.srm.creditengine.identity.api;

import com.srm.creditengine.identity.application.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
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
    AuthenticationService.AccessToken login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authentication.authenticate(
                request.email(), request.password(), httpRequest.getRemoteAddr());
    }

    record LoginRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 1024) String password) {}
}
