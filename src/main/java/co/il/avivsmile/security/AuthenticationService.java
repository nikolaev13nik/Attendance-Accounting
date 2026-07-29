package co.il.avivsmile.security;

import co.il.avivsmile.security.dto.LoginRequestDto;
import co.il.avivsmile.security.dto.LoginResponseDto;

public interface AuthenticationService {

    LoginResponseDto authenticate(LoginRequestDto request);
}
