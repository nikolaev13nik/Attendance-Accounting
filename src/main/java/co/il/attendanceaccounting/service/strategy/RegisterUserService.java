package co.il.attendanceaccounting.service.strategy;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.exceptions.TenantNotFoundException;
import co.il.attendanceaccounting.exceptions.UserExistsException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
import co.il.attendance.security.JwtClaimsResolver;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static co.il.attendance.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;
import static co.il.attendance.security.SecurityConstants.SecurityRoles.USER;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
public class RegisterUserService extends AccountServiceBase<UserRegisterDto> {

    private static final String FORBIDDEN = "Forbidden";

    @Override
    protected void fetch(AccountContext<UserRegisterDto> context) {
        context.setTenantHasUsers(userRepository.existsByUserBasicKeyTenantId(context.getTenantId()));
    }

    @Override
    protected void validate(AccountContext<UserRegisterDto> context) {
        Integer tenantId = context.getTenantId();
        ensureCallerMayRegisterInto(tenantId);
        if (isFalse(tenantMappingRepository.existsById(tenantId))) {
            throw new TenantNotFoundException(String.valueOf(tenantId));
        }
        if (userRepository.existsById(new UserBasicKey(tenantId, context.getTask().getIdUser()))) {
            throw new UserExistsException();
        }
    }

    private void ensureCallerMayRegisterInto(Integer tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isFalse(JwtClaimsResolver.isPlatformAdmin(auth)) && isFalse(JwtClaimsResolver.isAdministratorOf(auth, tenantId))) {
            throw new AccessDeniedException(FORBIDDEN);
        }
    }

    @Override
    protected void executeBusiness(AccountContext<UserRegisterDto> context) {
        User user = userMapper.toEntity(context.getTask());
        user.setUserBasicKey(new UserBasicKey(context.getTenantId(), context.getTask().getIdUser()));
        encodePassword(user, context.getTask().getPassword());
        user.setRoles(rolesForNewUser(context));
        context.getUsers().add(user);
    }

    private Set<String> rolesForNewUser(AccountContext<UserRegisterDto> context) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(USER.name());
        if (!context.isTenantHasUsers()) {
            roles.add(ADMINISTRATOR.name());
        }
        return roles;
    }

    @Override
    protected Integer targetUserId(AccountContext<UserRegisterDto> context) {
        return context.getTask().getIdUser();
    }
}
