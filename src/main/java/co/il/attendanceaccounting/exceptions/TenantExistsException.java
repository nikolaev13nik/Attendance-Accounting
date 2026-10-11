package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT, reason = "tenant exists")
public class TenantExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
