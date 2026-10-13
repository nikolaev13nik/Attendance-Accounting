package co.il.attendanceaccounting.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.exceptions.UserAuthenticationException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.security.dto.LoginRequestDto;
import co.il.attendanceaccounting.security.dto.LoginResponseDto;
import co.il.attendanceaccounting.service.TenantService;

@Service
public class AuthenticationServiceImpl implements AuthenticationService{

    private static  final String TOKEN_TYPE="Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantService tenantService;

    public AuthenticationServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService,
                                     UserRepository userRepository, TenantService tenantService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantService = tenantService;
    }

    /**
     * The organization the caller claims to belong to, given either as its identifier or by name.
     * A name that matches no organization resolves to null and is reported as invalid credentials,
     * the same as a wrong password, so neither can be used to discover which organizations exist.
     */
    private Integer resolveRequestedTenantId(LoginRequestDto request) {
        if (request.tenantId() != null) {
            return request.tenantId();
        }
        return tenantService.resolveTenantId(request.companyName());
    }

    @Override
    public LoginResponseDto authenticate(LoginRequestDto request) {
        if (request == null || request.idUser() == null || request.password() == null
                || (request.tenantId() == null && (request.companyName() == null || request.companyName().isBlank()))) {
            throw new UserAuthenticationException("Missing credentials");
        }
        Integer requestedTenantId = resolveRequestedTenantId(request);
            Authentication authentication;
            try {
                authentication =authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(String.valueOf(request.idUser()),request.password()));
            }catch (AuthenticationException ex){
                throw new UserAuthenticationException("Invalid credentials", ex);
            }
            User user = userRepository.findById(request.idUser())
                    .orElseThrow(() -> new UserAuthenticationException("Invalid credentials"));
        if (!user.getTenantId().equals(requestedTenantId)) {
            throw new UserAuthenticationException("Invalid credentials");
            }
            JwtService.MintedToken minted = jwtService.mint(authentication, user.getTenantId());
            return new LoginResponseDto(minted.token(),TOKEN_TYPE,minted.expiresIn(),null);
        }

}
