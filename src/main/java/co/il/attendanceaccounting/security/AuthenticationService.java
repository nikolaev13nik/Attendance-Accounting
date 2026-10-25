package co.il.attendanceaccounting.security;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.LoginRequestDto;

public interface AuthenticationService {

    void authenticate(AccountContext<LoginRequestDto> context);
}
