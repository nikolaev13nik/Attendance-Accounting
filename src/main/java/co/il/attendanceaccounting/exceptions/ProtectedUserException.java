package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.FORBIDDEN, reason = "user is protected and cannot be deleted")
public class ProtectedUserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

}
