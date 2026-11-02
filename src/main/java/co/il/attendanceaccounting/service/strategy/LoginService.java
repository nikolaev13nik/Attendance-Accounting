package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.LoginRequestDto;
import co.il.attendanceaccounting.model.TenantMapping;
import co.il.attendanceaccounting.security.AuthenticationService;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class LoginService extends AccountServiceBase<LoginRequestDto> {

    @Autowired
    private AuthenticationService authenticationService;

    @Override
    protected void fetch(AccountContext<LoginRequestDto> context) {
        LoginRequestDto request = context.getTask();
        findTenantMapping(request).ifPresentOrElse(tenantMapping -> {
            context.setTenantId(tenantMapping.getTenantId());
            context.setCompanyName(tenantMapping.getCompanyName());
        }, () -> context.setTenantId(request.getTenantId()));
    }

    @Override
    protected void validate(AccountContext<LoginRequestDto> context) {
        authenticationService.authenticate(context);
    }

    @Override
    protected void mapResult(AccountContext<LoginRequestDto> context) {
        context.getLoginResponseDto().setProfile(userMapper.toDto(context.getSingleUser(), context.getCompanyName()));
    }

    private Optional<TenantMapping> findTenantMapping(LoginRequestDto request) {
        if (request.getTenantId() == null) {
            String companyName = request.getCompanyName();
            if (companyName == null || companyName.isBlank()) {
                return Optional.empty();
            }
            return tenantMappingRepository.findByCompanyNameIgnoreCase(companyName.trim());
        }
        return tenantMappingRepository.findById(request.getTenantId());
    }

    @Override
    protected Integer targetUserId(AccountContext<LoginRequestDto> context) {
        return context.getTask().getIdUser();
    }
}
