package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.exceptions.ProtectedUserException;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class RemoveUserService extends AccountServiceBase<Void> {

    @Value("${attendance-accounting.bootstrap.admin.id:123456789}")
    private int protectedAdminId;

    @Override
    protected void fetch(AccountContext<Void> context) {
        userRepository.findById(context.getIdUser()).ifPresent(context.getUsers()::add);
    }

    @Override
    protected void validate(AccountContext<Void> context) {
        if (context.getIdUser() == protectedAdminId) {
            throw new ProtectedUserException();
        }
        if (context.getUsers().isEmpty()) {
            throw userNotFound(context.getIdUser());
        }
    }

    @Override
    protected void persist(AccountContext<Void> context) {
        userRepository.deleteAll(context.getUsers());
    }
}
