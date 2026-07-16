package com.srm.creditengine.identity.infrastructure;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
class DevelopmentOperatorSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    DevelopmentOperatorSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            @Value("${srm.dev-operator.email:}") String email,
            @Value("${srm.dev-operator.password:}") String password) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(
                "insert into users(id,email,password_hash,enabled) values (?,?,?,true) "
                        + "on conflict (email) do nothing",
                id,
                email,
                passwords.encode(password));
        if (inserted == 1) {
            jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, "OPERATOR");
        }
    }
}
