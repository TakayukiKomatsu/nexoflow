package com.srm.creditengine.identity.infrastructure;

import com.srm.creditengine.identity.domain.IdentityAccount;
import com.srm.creditengine.identity.application.TokenIssuer;
import java.time.Clock;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService implements TokenIssuer {
    public static final long TTL_SECONDS = 900;

    private final JwtEncoder encoder;
    private final Clock clock;

    JwtTokenService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public String issue(IdentityAccount account) {
        var issuedAt = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject(account.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(TTL_SECONDS))
                .claim("email", account.email())
                .claim("roles", account.roles())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
