package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.service.base.RoleServiceBase;

@Service
public class AddRoleService extends RoleServiceBase {

    @Override
    protected void executeBusiness(AccountContext<Void> context) {
        context.getSingleUser().addRole(context.getRole());
    }
}
