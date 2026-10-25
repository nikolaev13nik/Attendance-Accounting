package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.exceptions.TenantNotFoundException;
import co.il.attendanceaccounting.exceptions.UserExistsException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.USER;

@Service
public class RegisterUserService extends AccountServiceBase<UserRegisterDto> {

    @Override
    protected void fetch(AccountContext<UserRegisterDto> context) {
        context.setTenantHasUsers(userRepository.existsByTenantId(context.getTask().getTenantId()));
    }

    @Override
    protected void validate(AccountContext<UserRegisterDto> context) {
        UserRegisterDto userRegisterDto = context.getTask();
        if (userRepository.existsById(userRegisterDto.getIdUser())) {
            throw new UserExistsException();
        }
        Integer tenantId = userRegisterDto.getTenantId();
        if (tenantId == null || !tenantMappingRepository.existsById(tenantId)) {
            throw new TenantNotFoundException(String.valueOf(tenantId));
        }
    }

    @Override
    protected void executeBusiness(AccountContext<UserRegisterDto> context) {
        User user = userMapper.toEntity(context.getTask());
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
}
