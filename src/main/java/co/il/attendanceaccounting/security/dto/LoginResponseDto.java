package co.il.attendanceaccounting.security.dto;

import co.il.attendanceaccounting.dto.UserProfileDto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDto {

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserProfileDto profile;
}
