package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "tenant must keep at least one administrator")
public class LastAdministratorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
