package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class GetAllUsersService extends AccountServiceBase<Void> {

    @Override
    protected void fetch(AccountContext<Void> context) {
        context.setUsers(context.getTenantId() == null
                ? userRepository.findAll()
                : userRepository.findByTenantId(context.getTenantId()));
    }
}
