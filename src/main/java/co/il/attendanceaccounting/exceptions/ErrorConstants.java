package co.il.attendanceaccounting.exceptions;

public class ErrorConstants {

    public static final String USER_NOT_FOUND_MSG = "User with login = %s not found";
    public static final String TENANT_NOT_FOUND_MSG = "Tenant with id = %s not found";
    public static final String ROLE_NOT_ALLOWED_MSG = "Role %s is not allowed";
    public static final String MISSING_CREDENTIALS_MSG = "Missing credentials";
    public static final String INVALID_CREDENTIALS_MSG = "Invalid credentials";
    public static final String CAPTCHA_REJECTED_MSG = "Captcha check failed. Reload the page and try again";
    public static final String CAPTCHA_UNAVAILABLE_MSG = "The captcha could not be verified right now. Try again in a minute";
    public static final String INVALID_PAGE_MSG = "Page %s of size %s is not allowed: page must be 0 or more, size 1 to 100";

}
