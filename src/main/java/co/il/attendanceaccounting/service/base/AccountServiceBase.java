package co.il.attendanceaccounting.service.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dao.TenantMappingRepository;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.exceptions.UserNotFoundException;
import co.il.attendanceaccounting.mapper.UserMapper;
import co.il.attendanceaccounting.model.User;
import jakarta.transaction.Transactional;

public abstract class AccountServiceBase<R> implements BaseService<R> {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TenantMappingRepository tenantMappingRepository;
    @Autowired
    protected UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

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

    protected User findUserOrThrow(Integer idUser) {
        return userRepository.findById(idUser).orElseThrow(() -> userNotFound(idUser));
    }

    protected UserNotFoundException userNotFound(Integer idUser) {
        return new UserNotFoundException(idUser.toString());
    }

    protected void encodePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
    }
}
