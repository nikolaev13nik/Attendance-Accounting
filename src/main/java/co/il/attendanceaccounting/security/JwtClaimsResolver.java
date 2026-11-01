package co.il.attendanceaccounting.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

import static co.il.attendanceaccounting.security.SecurityConstants.PLATFORM_TENANT_ID;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.PLATFORM_ADMIN;
import static co.il.attendanceaccounting.security.SecurityConstants.TENANT_ID;

public final class JwtClaimsResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    private JwtClaimsResolver() {
    }

    public static String currentTenantId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString(TENANT_ID);
        }
        return null;
    }

    public static boolean isPlatformAdmin(Authentication auth) {
        return PLATFORM_TENANT_ID.toString().equals(currentTenantId(auth)) && hasAuthority(auth, PLATFORM_ADMIN);
    }

    public static boolean isAdministratorOf(Authentication auth, Integer tenantId) {
        return tenantId != null && tenantId.toString().equals(currentTenantId(auth))
                && hasAuthority(auth, ADMINISTRATOR);
    }

    private static boolean hasAuthority(Authentication auth, SecurityConstants.SecurityRoles role) {
        String authority = ROLE_PREFIX + role.name();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(authority::equals);
    }
}
