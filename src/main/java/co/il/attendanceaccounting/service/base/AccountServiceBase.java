package co.il.attendanceaccounting.service.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Optional;

import co.il.attendance.logging.LogContextScope;
import co.il.attendanceaccounting.configuration.AccountConfiguration;
import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dao.TenantMappingRepository;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.exceptions.LastAdministratorException;
import co.il.attendanceaccounting.exceptions.UserNotFoundException;
import co.il.attendanceaccounting.mapper.UserMapper;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;

import static co.il.attendance.logging.LogContext.TARGET_USER_ID;
import static co.il.attendance.security.SecurityConstants.PLATFORM_TENANT_ID;
import static co.il.attendance.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;

public abstract class AccountServiceBase<R> implements BaseService<R> {

    private final Logger log = LoggerFactory.getLogger(getClass());

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
        try (LogContextScope ignored = LogContextScope.open().with(TARGET_USER_ID, targetUserId(context))) {
            try {
                fetch(context);
                validate(context);
                executeBusiness(context);
                persist(context);
                mapResult(context);
                log.info("Api completed");
            } catch (RuntimeException e) {
                logFailure(e);
                throw e;
            }
        }
    }

    protected Integer targetUserId(AccountContext<R> context) {
        UserBasicKey key = context.getUserBasicKey();
        return key == null ? null : key.getIdUser();
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

    private void logFailure(RuntimeException e) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (status != null && status.code().is4xxClientError()) {
            log.warn("rejected: {}", status.reason().isEmpty() ? e.getMessage() : status.reason());
            return;
        }
        log.error("failed", e);
    }
}
