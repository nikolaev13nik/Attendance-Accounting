package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.service.base.RoleServiceBase;

import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;

@Service
public class RemoveRoleService extends RoleServiceBase {

    @Override
    protected void validate(AccountContext<Void> context) {
        super.validate(context);
        if (ADMINISTRATOR.name().equals(context.getRole())) {
            ensureNotLastAdministrator(context.getSingleUser());
        }
    }

    @Override
    protected void executeBusiness(AccountContext<Void> context) {
        context.getSingleUser().removeRole(context.getRole());
    }
}
