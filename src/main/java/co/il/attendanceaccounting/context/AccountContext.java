package co.il.attendanceaccounting.context;

import java.util.ArrayList;
import java.util.List;

import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.model.TenantMapping;
import co.il.attendanceaccounting.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountContext<T> implements BusinessStrategyContext {

    private T task;
    private Integer idUser;
    private Integer tenantId;
    private String role;
    @Builder.Default
    private List<User> users = new ArrayList<>();
    @Builder.Default
    private List<TenantMapping> tenantMappings = new ArrayList<>();
    private boolean tenantHasUsers;
    private String companyName;

    @Builder.Default
    private List<UserProfileDto> responseUserProfileDto = new ArrayList<>();
    @Builder.Default
    private List<TenantMappingDto> responseTenantMappingDto = new ArrayList<>();
    private LoginResponseDto loginResponseDto;

    public User getSingleUser() {
        return this.users.get(0);
    }

    public UserProfileDto getSingleResponseUserProfileDto() {
        return this.responseUserProfileDto.get(0);
    }

    public TenantMappingDto getSingleResponseTenantMappingDto() {
        return this.responseTenantMappingDto.get(0);
    }
}
