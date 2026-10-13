package co.il.attendanceaccounting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TenantNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TenantNotFoundException(String tenantId) {
		super("Tenant with id = " + tenantId + " not found");
	}

}
