package co.il.avivsmile.security;

import co.il.avivsmile.exceptions.UserAuthenticationException;
import co.il.avivsmile.security.dto.LoginRequestDto;
import co.il.avivsmile.security.dto.LoginResponseDto;
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

    public AuthenticationServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponseDto authenticate(LoginRequestDto request) {
        if (request == null || request.idUser() == null || request.password() == null) {
            throw new UserAuthenticationException("Missing credentials", null);
        }
            Authentication authentication;
            try {
                authentication =authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(String.valueOf(request.idUser()),request.password()));
            }catch (AuthenticationException ex){
                throw new UserAuthenticationException("Invalid credentials", ex);
            }
            JwtService.MintedToken minted = jwtService.mint(authentication);
            return new LoginResponseDto(minted.token(),TOKEN_TYPE,minted.expiresIn(),null);
        }

}
