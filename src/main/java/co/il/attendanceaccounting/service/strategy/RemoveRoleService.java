package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class RemoveRoleService extends AccountServiceBase<Void> {

    @Override
    protected void fetch(AccountContext<Void> context) {
        context.getUsers().add(findUserOrThrow(context.getIdUser()));
    }

    @Override
    protected void executeBusiness(AccountContext<Void> context) {
        context.getSingleUser().removeRole(context.getRole());
    }
}
