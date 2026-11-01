package co.il.attendanceaccounting.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import static co.il.attendanceaccounting.security.SecurityConstants.TENANT_ID;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String FORBIDDEN = "Forbidden";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String pathTenantId = pathTenantId(request);
        if (pathTenantId == null) {
            log.warn("Request rejected: no tenant in path {}", request.getRequestURI());
            throw new AccessDeniedException(FORBIDDEN);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String jwtTenantId = JwtClaimsResolver.currentTenantId(auth);
        if (JwtClaimsResolver.isPlatformAdmin(auth)) {
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
}
