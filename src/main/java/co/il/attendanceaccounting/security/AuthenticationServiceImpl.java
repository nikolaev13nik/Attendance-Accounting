package co.il.attendanceaccounting.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dao.TenantMappingRepository;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.dto.LoginRequestDto;
import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.exceptions.UserAuthenticationException;
import co.il.attendanceaccounting.model.TenantMapping;
import co.il.attendanceaccounting.model.User;
import lombok.RequiredArgsConstructor;

import static co.il.attendanceaccounting.exceptions.ErrorConstants.INVALID_CREDENTIALS_MSG;
import static co.il.attendanceaccounting.exceptions.ErrorConstants.MISSING_CREDENTIALS_MSG;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantMappingRepository tenantMappingRepository;

    private Integer resolveRequestedTenantId(LoginRequestDto request) {
        if (request.getTenantId() != null) {
            return request.getTenantId();
        }
        String companyName = request.getCompanyName();
        if (companyName == null || companyName.isBlank()) {
            return null;
        }
        return tenantMappingRepository.findByCompanyNameIgnoreCase(companyName.trim())
                .map(TenantMapping::getTenantId)
                .orElse(null);
    }

    @Override
    public void authenticate(AccountContext<LoginRequestDto> context) {
        LoginRequestDto request = context.getTask();
        if (request == null || request.getIdUser() == null || request.getPassword() == null
                || (request.getTenantId() == null
                && (request.getCompanyName() == null || request.getCompanyName().isBlank()))) {
            throw new UserAuthenticationException(MISSING_CREDENTIALS_MSG);
        }
        Integer requestedTenantId = resolveRequestedTenantId(request);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(String.valueOf(request.getIdUser()),
                            request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new UserAuthenticationException(INVALID_CREDENTIALS_MSG, ex);
        }
        User user = userRepository.findById(request.getIdUser())
                .orElseThrow(() -> new UserAuthenticationException(INVALID_CREDENTIALS_MSG));
        if (!user.getTenantId().equals(requestedTenantId)) {
            throw new UserAuthenticationException(INVALID_CREDENTIALS_MSG);
        }
        JwtService.MintedToken minted = jwtService.mint(authentication, user.getTenantId());
        context.setLoginResponseDto(LoginResponseDto.builder()
                .token(minted.token())
                .tokenType(TOKEN_TYPE)
                .expiresIn(minted.expiresIn())
                .build());
    }

}
