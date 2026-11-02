package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.SignUpRequestDto;
import co.il.attendanceaccounting.exceptions.TenantExistsException;
import co.il.attendanceaccounting.mapper.TenantMappingMapper;
import co.il.attendanceaccounting.model.TenantMapping;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static co.il.attendance.security.SecurityConstants.SecurityRoles.ADMINISTRATOR;
import static co.il.attendance.security.SecurityConstants.SecurityRoles.USER;

@Service
public class SignUpService extends AccountServiceBase<SignUpRequestDto> {

    @Autowired
    private TenantMappingMapper tenantMappingMapper;

    @Override
    protected void validate(AccountContext<SignUpRequestDto> context) {
        SignUpRequestDto request = context.getTask();
        if (tenantMappingRepository.existsByCompanyNameIgnoreCase(request.getCompanyName().trim())) {
            throw new TenantExistsException();
        }
    }

    @Override
    protected void executeBusiness(AccountContext<SignUpRequestDto> context) {
        SignUpRequestDto request = context.getTask();
        User admin = userMapper.toEntity(request);
        encodePassword(admin, request.getPassword());
        admin.setRoles(new LinkedHashSet<>(List.of(USER.name(), ADMINISTRATOR.name())));
        context.getTenantMappings().add(tenantMappingMapper.toEntity(request));
        context.getUsers().add(admin);
    }

    @Override
    protected void persist(AccountContext<SignUpRequestDto> context) {
        TenantMapping tenant = context.getTenantMappings().get(0);
        tenantMappingRepository.save(tenant);
        context.getSingleUser().setUserBasicKey(new UserBasicKey(tenant.getTenantId(), context.getTask().getIdUser()));
        userRepository.saveAll(context.getUsers());
    }

    @Override
    protected void mapResult(AccountContext<SignUpRequestDto> context) {
        super.mapResult(context);
        context.getResponseTenantMappingDto().add(tenantMappingMapper.toDto(context.getTenantMappings().get(0)));
    }

    @Override
    protected Integer targetUserId(AccountContext<SignUpRequestDto> context) {
        return context.getTask().getIdUser();
    }
}
