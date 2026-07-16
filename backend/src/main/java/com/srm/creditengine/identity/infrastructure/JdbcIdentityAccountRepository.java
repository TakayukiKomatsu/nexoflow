package com.srm.creditengine.identity.infrastructure;

import com.srm.creditengine.identity.application.IdentityAccountRepository;
import com.srm.creditengine.identity.domain.IdentityAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdentityAccountRepository implements IdentityAccountRepository {
    private final JdbcTemplate jdbc;

    JdbcIdentityAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdentityAccount> findEnabledByEmail(String email) {
        var users = jdbc.query(
                "select id,email,password_hash from users where lower(email)=lower(?) and enabled=true",
                (rs, row) -> new AccountRow(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                email);
        if (users.size() != 1) {
            return Optional.empty();
        }
        var user = users.getFirst();
        var roles = jdbc.queryForList(
                "select role from user_roles where user_id=? order by role", String.class, user.id());
        return Optional.of(new IdentityAccount(user.id(), user.email(), user.passwordHash(), roles));
    }

    private record AccountRow(UUID id, String email, String passwordHash) {}
}
