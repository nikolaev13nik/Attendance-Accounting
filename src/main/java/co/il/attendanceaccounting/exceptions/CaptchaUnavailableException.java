package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CaptchaUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CaptchaUnavailableException(Throwable cause) {
		super(ErrorConstants.CAPTCHA_UNAVAILABLE_MSG, cause);
	}
}
