package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class GetUserService extends AccountServiceBase<Void> {

    @Override
    protected void fetch(AccountContext<Void> context) {
        context.getUsers().add(userRepository.findByIdUserAndTenantId(context.getIdUser(), context.getTenantId())
                .orElseThrow(() -> userNotFound(context.getIdUser())));
    }
}
