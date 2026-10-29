package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RoleNotAllowedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RoleNotAllowedException(String role) {
		super(String.format(ErrorConstants.ROLE_NOT_ALLOWED_MSG, role));
	}

}
