package co.il.attendanceaccounting.service.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import co.il.attendanceaccounting.configuration.AccountConfiguration;
import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dao.TenantMappingRepository;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.exceptions.LastAdministratorException;
import co.il.attendanceaccounting.exceptions.UserNotFoundException;
import co.il.attendanceaccounting.mapper.UserMapper;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
import jakarta.transaction.Transactional;

import static co.il.attendance.security.SecurityConstants.PLATFORM_TENANT_ID;
import static co.il.attendance.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;

public abstract class AccountServiceBase<R> implements BaseService<R> {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TenantMappingRepository tenantMappingRepository;
    @Autowired
    protected UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AccountConfiguration accountConfiguration;

    @Transactional
    @Override
    public void execute(AccountContext<R> context) {
        executeWithoutTransactional(context);
    }

    @Override
    public void executeWithoutTransactional(AccountContext<R> context) {
        fetch(context);
        validate(context);
        executeBusiness(context);
        persist(context);
        mapResult(context);
    }

    protected void fetch(AccountContext<R> context) {
    }

    protected void validate(AccountContext<R> context) {
    }

    protected void executeBusiness(AccountContext<R> context) {
    }

    protected void persist(AccountContext<R> context) {
        userRepository.saveAll(context.getUsers());
        tenantMappingRepository.saveAll(context.getTenantMappings());
    }

    protected void mapResult(AccountContext<R> context) {
        context.setResponseUserProfileDto(userMapper.toDtoList(context.getUsers()));
    }

    protected Optional<User> findTenantUser(AccountContext<R> context) {
        return userRepository.findById(context.getUserBasicKey());
    }

    protected User findUserOrThrow(AccountContext<R> context) {
        return findTenantUser(context).orElseThrow(() -> userNotFound(context.getUserBasicKey().getIdUser()));
    }

    protected boolean isProtectedUser(AccountContext<R> context) {
        UserBasicKey key = context.getUserBasicKey();
        return key.getIdUser() == accountConfiguration.getBootstrapAdminId()
                && PLATFORM_TENANT_ID.equals(key.getTenantId());
    }

    protected void ensureNotLastAdministrator(User user) {
        if (user.hasRole(ADMINISTRATOR.name())
                && userRepository.countByTenantIdAndRole(user.getTenantId(), ADMINISTRATOR.name()) <= 1) {
            throw new LastAdministratorException();
        }
    }

    protected UserNotFoundException userNotFound(Integer idUser) {
        return new UserNotFoundException(idUser.toString());
    }

    protected void encodePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
    }
}
