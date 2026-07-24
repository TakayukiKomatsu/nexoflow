package com.srm.creditengine.identity.api;

import com.srm.creditengine.identity.application.AuthenticationService;
import io.swagger.v3.oas.annotations.media.Schema;
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
    AccessTokenResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return AccessTokenResponse.from(authentication.authenticate(
                request.email(), request.password(), httpRequest.getRemoteAddr()));
    }

    record LoginRequest(
            @Email @NotBlank @Size(min = 1, max = 254) @Schema(minLength = 1, maxLength = 254) String email,
            @NotBlank @Size(min = 1, max = 1024) @Schema(minLength = 1, maxLength = 1024) String password) {}

    @Schema(name = "AccessToken", requiredProperties = {"accessToken", "tokenType", "expiresIn"})
    record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
        static AccessTokenResponse from(AuthenticationService.AccessToken token) {
            return new AccessTokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
        }
    }
}
