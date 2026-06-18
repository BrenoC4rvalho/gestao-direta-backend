package br.com.gestaodireta.auth.security;

import br.com.gestaodireta.user.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final String issuer;

    private final long expirationMinutes;

    private final Clock clock;

    private final Algorithm algorithm;

    @Autowired
    public JwtService(
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this(issuer, secret, expirationMinutes, Clock.systemUTC());
    }

    JwtService(String issuer, String secret, long expirationMinutes, Clock clock) {
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
        this.clock = clock;
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String generateToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES);

        return JWT.create()
                .withIssuer(issuer)
                .withSubject(String.valueOf(user.getId()))
                .withClaim("email", user.getEmail())
                .withClaim("userType", user.getUserType().name())
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    public DecodedJWT verify(String token) {
        JWTVerifier verifier = JWT.require(algorithm).withIssuer(issuer).build();

        return verifier.verify(token);
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
