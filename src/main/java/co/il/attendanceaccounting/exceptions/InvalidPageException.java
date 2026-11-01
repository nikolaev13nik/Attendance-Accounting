package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPageException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidPageException(Integer page, Integer size) {
		super(String.format(ErrorConstants.INVALID_PAGE_MSG, page, size));
	}

}
