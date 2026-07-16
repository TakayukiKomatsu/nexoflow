package com.srm.creditengine.identity.api;
import java.net.URI; import java.time.Instant; import java.util.Map;
import jakarta.validation.Valid; import jakarta.validation.constraints.Email; import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth") class LoginController {
 private final JdbcTemplate jdbc; private final PasswordEncoder passwords; private final JwtTokenService tokens;
 LoginController(JdbcTemplate jdbc, PasswordEncoder passwords, JwtTokenService tokens){this.jdbc=jdbc;this.passwords=passwords;this.tokens=tokens;}
 @PostMapping("/login") ResponseEntity<?> login(@Valid @RequestBody LoginRequest request){
  var users=jdbc.query("select id,password_hash from users where email=? and enabled=true",(rs,n)->new User(rs.getString(1),rs.getString(2)),request.email());
  if(users.size()!=1||!passwords.matches(request.password(),users.getFirst().hash())) return invalid();
  var roles=jdbc.queryForList("select role from user_roles where user_id=?",String.class,java.util.UUID.fromString(users.getFirst().id()));
  return ResponseEntity.ok(Map.of("accessToken",tokens.issue(users.getFirst().id(),roles),"tokenType","Bearer","expiresIn",900)); }
 private ResponseEntity<ProblemDetail> invalid(){var p=ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,"Invalid credentials.");p.setType(URI.create("urn:srm:error:invalid-credentials"));p.setProperty("code","INVALID_CREDENTIALS");return ResponseEntity.status(401).body(p);}
 record LoginRequest(@Email @NotBlank String email,@NotBlank String password){} record User(String id,String hash){}
}
