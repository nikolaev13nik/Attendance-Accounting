package co.il.attendanceaccounting.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.dto.LoginRequestDto;
import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.exceptions.UserAuthenticationException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
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

    @Override
    public void authenticate(AccountContext<LoginRequestDto> context) {
        LoginRequestDto request = context.getTask();
        if (request.getTenantId() == null
                && (request.getCompanyName() == null || request.getCompanyName().isBlank())) {
            throw new UserAuthenticationException(MISSING_CREDENTIALS_MSG);
        }
        Integer requestedTenantId = context.getTenantId();
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    UserDetailsServiceImpl.principalOf(requestedTenantId, request.getIdUser()), request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new UserAuthenticationException(INVALID_CREDENTIALS_MSG, ex);
        }
        User user = userRepository.findById(new UserBasicKey(requestedTenantId, request.getIdUser()))
                .orElseThrow(() -> new UserAuthenticationException(INVALID_CREDENTIALS_MSG));
        context.getUsers().add(user);
        JwtService.MintedToken minted = jwtService.mint(authentication, user.getTenantId());
        context.setLoginResponseDto(LoginResponseDto.builder()
                .token(minted.token())
                .tokenType(TOKEN_TYPE)
                .expiresIn(minted.expiresIn())
                .build());
    }

}
