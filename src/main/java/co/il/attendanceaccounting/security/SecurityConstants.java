package co.il.attendanceaccounting.security;

public class SecurityConstants {


    public static final String AUTHORITIES = "authorities";

    public static final String TENANT_ID = "tenantId";

    public static final Integer SYSTEM_TENANT_ID = 0;

    public enum SecurityRoles {
        USER,
        MODERATOR,
        ADMINISTRATOR
    }


}
