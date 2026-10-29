package co.il.attendanceaccounting.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import static co.il.attendanceaccounting.security.SecurityConstants.SYSTEM_TENANT_ID;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.PLATFORM_ADMIN;
import static co.il.attendanceaccounting.security.SecurityConstants.TENANT_ID;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_" + PLATFORM_ADMIN.name();
    private static final String FORBIDDEN = "Forbidden";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String pathTenantId = pathTenantId(request);
        if (pathTenantId == null) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String jwtTenantId = jwtTenantId(auth);
        if (isPlatformAdmin(auth, jwtTenantId)) {
            log.info("Platform admin {} acting on tenant {}: {} {}", auth.getName(), pathTenantId,
                    request.getMethod(), request.getRequestURI());
            return true;
        }
        if (jwtTenantId == null || !jwtTenantId.equals(pathTenantId)) {
            log.warn("Request rejected: JWT tenant {} does not match path tenant {} on {}", jwtTenantId, pathTenantId,
                    request.getRequestURI());
            throw new AccessDeniedException(FORBIDDEN);
        }
        return true;
    }

    private static String pathTenantId(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> pathVars) {
            Object tenantId = pathVars.get(TENANT_ID);
            return tenantId != null ? tenantId.toString() : null;
        }
        return null;
    }

    private static String jwtTenantId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString(TENANT_ID);
        }
        return null;
    }

    private static boolean isPlatformAdmin(Authentication auth, String jwtTenantId) {
        return SYSTEM_TENANT_ID.toString().equals(jwtTenantId) && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .anyMatch(PLATFORM_ADMIN_AUTHORITY::equals);
    }
}
