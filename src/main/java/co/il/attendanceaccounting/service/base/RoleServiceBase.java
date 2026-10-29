package co.il.attendanceaccounting.service.base;

import java.util.Locale;
import java.util.Set;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.exceptions.ProtectedUserException;
import co.il.attendanceaccounting.exceptions.RoleNotAllowedException;

import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.USER;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

public abstract class RoleServiceBase extends AccountServiceBase<Void> {

    private static final Set<String> GRANTABLE_ROLES = Set.of(USER.name(), ADMINISTRATOR.name());

    @Override
    protected void fetch(AccountContext<Void> context) {
        context.getUsers().add(findUserOrThrow(context));
    }

    @Override
    protected void validate(AccountContext<Void> context) {
        String role = context.getRole().trim().toUpperCase(Locale.ROOT);
        if (isFalse(GRANTABLE_ROLES.contains(role))) {
            throw new RoleNotAllowedException(context.getRole());
        }
        context.setRole(role);
        if (isProtectedUser(context.getIdUser())) {
            throw new ProtectedUserException();
        }
    }
}
