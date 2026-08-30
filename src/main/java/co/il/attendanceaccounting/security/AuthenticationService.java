package co.il.attendanceaccounting.security;

import co.il.attendanceaccounting.security.dto.LoginRequestDto;
import co.il.attendanceaccounting.security.dto.LoginResponseDto;

public interface AuthenticationService {

    LoginResponseDto authenticate(LoginRequestDto request);
}
