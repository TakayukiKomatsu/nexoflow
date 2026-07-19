package com.srm.creditengine.identity.infrastructure;

import com.srm.creditengine.identity.application.ActorContext;
import com.srm.creditengine.identity.application.ActorRole;
import com.srm.creditengine.identity.application.CurrentActor;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
class SpringActorContext implements ActorContext {
    @Override
    public CurrentActor currentActor() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Set<ActorRole> roles = jwt.getClaimAsStringList("roles").stream().map(ActorRole::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new CurrentActor(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), roles);
    }
}
