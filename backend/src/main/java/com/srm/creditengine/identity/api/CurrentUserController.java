package com.srm.creditengine.identity.api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class CurrentUserController {
    @GetMapping("/me")
    CurrentUser currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsStringList("roles"));
    }

    record CurrentUser(String id, String email, List<String> roles) {}
}
