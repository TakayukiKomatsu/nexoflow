package com.srm.creditengine.identity.infrastructure;

import java.util.UUID;
import java.util.Set;
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
    private final String operatorEmail;
    private final String operatorPassword;
    private final String adminEmail;
    private final String adminPassword;

    DevelopmentOperatorSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            @Value("${srm.dev-operator.email:}") String operatorEmail,
            @Value("${srm.dev-operator.password:}") String operatorPassword,
            @Value("${srm.dev-admin.email:}") String adminEmail,
            @Value("${srm.dev-admin.password:}") String adminPassword) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.operatorEmail = operatorEmail;
        this.operatorPassword = operatorPassword;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(operatorEmail, operatorPassword, Set.of("OPERATOR"));
        seed(adminEmail, adminPassword, Set.of("ADMIN"));
    }

    private void seed(String email, String password, Set<String> requiredRoles) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        UUID generatedId = UUID.randomUUID();
        int inserted = jdbc.update(
                "insert into users(id,email,password_hash,enabled) values (?,?,?,true) "
                        + "on conflict (email) do nothing",
                generatedId,
                email,
                passwords.encode(password));
        UUID userId = inserted == 1
                ? generatedId
                : jdbc.queryForObject("select id from users where email=?", UUID.class, email);
        requiredRoles.forEach(role -> jdbc.update(
                "insert into user_roles(user_id,role) values (?,?) on conflict (user_id,role) do nothing",
                userId,
                role));
    }
}
