package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.exceptions.ProtectedUserException;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class RemoveUserService extends AccountServiceBase<Void> {

    @Override
    protected void fetch(AccountContext<Void> context) {
        findTenantUser(context).ifPresent(context.getUsers()::add);
    }

    @Override
    protected void validate(AccountContext<Void> context) {
        if (isProtectedUser(context)) {
            throw new ProtectedUserException();
        }
        if (context.getUsers().isEmpty()) {
            throw userNotFound(context.getUserBasicKey().getIdUser());
        }
        ensureNotLastAdministrator(context.getSingleUser());
    }

    @Override
    protected void persist(AccountContext<Void> context) {
        userRepository.deleteAll(context.getUsers());
    }
}
