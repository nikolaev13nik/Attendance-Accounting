package co.il.attendanceaccounting.security;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.exceptions.UserAuthenticationException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.security.dto.LoginRequestDto;
import co.il.attendanceaccounting.security.dto.LoginResponseDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationServiceImpl implements AuthenticationService{

    private static  final String TOKEN_TYPE="Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthenticationServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public LoginResponseDto authenticate(LoginRequestDto request) {
        if (request == null || request.idUser() == null || request.password() == null || request.tenantId() == null) {
            throw new UserAuthenticationException("Missing credentials", null);
        }
            Authentication authentication;
            try {
                authentication =authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(String.valueOf(request.idUser()),request.password()));
            }catch (AuthenticationException ex){
                throw new UserAuthenticationException("Invalid credentials", ex);
            }
            User user = userRepository.findById(request.idUser())
                    .orElseThrow(() -> new UserAuthenticationException("Invalid credentials", null));
            if (!user.getTenantId().equals(request.tenantId())) {
                throw new UserAuthenticationException("Invalid credentials", null);
            }
            JwtService.MintedToken minted = jwtService.mint(authentication, user.getTenantId());
            return new LoginResponseDto(minted.token(),TOKEN_TYPE,minted.expiresIn(),null);
        }

}
