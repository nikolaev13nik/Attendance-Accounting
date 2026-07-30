package co.il.avivsmile.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static co.il.avivsmile.security.SecurityConstants.AUTHORITIES;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${avivsmile.security.jwt.ttl-seconds}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = ttlSeconds;
    }

    public MintedToken mint(Authentication authentication){
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AUTHORITIES, authorities)
                .build();
        JwsHeader header  = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        return new MintedToken(token,ttlSeconds);
    }
    public record MintedToken(String token,long expiresIn){

    }
}
