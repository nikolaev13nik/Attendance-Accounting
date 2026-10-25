package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        if (request == null || request.getIdUser() == null) {
            return;
        }
        userRepository.findById(request.getIdUser()).ifPresent(user -> {
            context.getUsers().add(user);
            if (user.getTenantId() != null) {
                context.setCompanyName(tenantMappingRepository.findById(user.getTenantId())
                        .map(TenantMapping::getCompanyName)
                        .orElse(null));
            }
        });
    }

    @Override
    protected void validate(AccountContext<LoginRequestDto> context) {
        authenticationService.authenticate(context);
    }

    @Override
    protected void mapResult(AccountContext<LoginRequestDto> context) {
        context.getLoginResponseDto().setProfile(userMapper.toDto(context.getSingleUser(), context.getCompanyName()));
    }
}
