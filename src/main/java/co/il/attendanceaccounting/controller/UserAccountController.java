package co.il.attendanceaccounting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import co.il.attendanceaccounting.api.AccountApi;
import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.LoginRequestDto;
import co.il.attendanceaccounting.dto.LoginResponseDto;
import co.il.attendanceaccounting.dto.TenantAddDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.service.strategy.AddRoleService;
import co.il.attendanceaccounting.service.strategy.AddTenantService;
import co.il.attendanceaccounting.service.strategy.EditUserService;
import co.il.attendanceaccounting.service.strategy.GetAllUsersService;
import co.il.attendanceaccounting.service.strategy.GetUserService;
import co.il.attendanceaccounting.service.strategy.LoginService;
import co.il.attendanceaccounting.service.strategy.RegisterUserService;
import co.il.attendanceaccounting.service.strategy.RemoveRoleService;
import co.il.attendanceaccounting.service.strategy.RemoveUserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserAccountController implements AccountApi {

    private final RegisterUserService registerUserService;
    private final AddTenantService addTenantService;
    private final RemoveUserService removeUserService;
    private final EditUserService editUserService;
    private final AddRoleService addRoleService;
    private final RemoveRoleService removeRoleService;
    private final GetAllUsersService getAllUsersService;
    private final GetUserService getUserService;
    private final LoginService loginService;

    @Override
    public ResponseEntity<UserProfileDto> register(@RequestBody UserRegisterDto userRegisterDto) {
        AccountContext<UserRegisterDto> context = AccountContext.<UserRegisterDto>builder()
                .task(userRegisterDto).build();
        registerUserService.execute(context);
        return ResponseEntity.status(HttpStatus.CREATED).body(context.getSingleResponseUserProfileDto());
    }

    @Override
    public ResponseEntity<TenantMappingDto> addTenant(@RequestBody TenantAddDto tenantAddDto) {
        AccountContext<TenantAddDto> context = AccountContext.<TenantAddDto>builder()
                .task(tenantAddDto).build();
        addTenantService.execute(context);
        return ResponseEntity.status(HttpStatus.CREATED).body(context.getSingleResponseTenantMappingDto());
    }

    @PreAuthorize("hasRole(T(co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles).ADMINISTRATOR.name())")
    @Override
    public ResponseEntity<UserProfileDto> removeUser(@PathVariable Integer idUser) {
        AccountContext<Void> context = AccountContext.<Void>builder().idUser(idUser).build();
        removeUserService.execute(context);
        return ResponseEntity.ok(context.getSingleResponseUserProfileDto());
    }

    @PreAuthorize("hasRole(T(co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles).ADMINISTRATOR.name()) || #idUser.toString() == authentication.name")
    @Override
    public ResponseEntity<UserProfileDto> editUser(@PathVariable Integer idUser,
                                                   @RequestBody UserEditDto userEditDto) {
        AccountContext<UserEditDto> context = AccountContext.<UserEditDto>builder()
                .idUser(idUser).task(userEditDto).build();
        editUserService.execute(context);
        return ResponseEntity.ok(context.getSingleResponseUserProfileDto());
    }

    @PreAuthorize("hasRole(T(co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles).ADMINISTRATOR.name())")
    @Override
    public ResponseEntity<UserProfileDto> addRole(@PathVariable Integer idUser, @PathVariable String role) {
        AccountContext<Void> context = AccountContext.<Void>builder().idUser(idUser).role(role).build();
        addRoleService.execute(context);
        return ResponseEntity.ok(context.getSingleResponseUserProfileDto());
    }

    @PreAuthorize("hasRole(T(co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles).ADMINISTRATOR.name())")
    @Override
    public ResponseEntity<UserProfileDto> removeRole(@PathVariable Integer idUser, @PathVariable String role) {
        AccountContext<Void> context = AccountContext.<Void>builder().idUser(idUser).role(role).build();
        removeRoleService.execute(context);
        return ResponseEntity.ok(context.getSingleResponseUserProfileDto());
    }

    @Override
    public ResponseEntity<List<UserProfileDto>> getAllUsers(@RequestParam(required = false) Integer tenantId) {
        AccountContext<Void> context = AccountContext.<Void>builder().tenantId(tenantId).build();
        getAllUsersService.execute(context);
        return ResponseEntity.ok(context.getResponseUserProfileDto());
    }

    @Override
    public ResponseEntity<UserProfileDto> getUser(@PathVariable Integer tenantId, @PathVariable Integer idUser) {
        AccountContext<Void> context = AccountContext.<Void>builder().tenantId(tenantId).idUser(idUser).build();
        getUserService.execute(context);
        return ResponseEntity.ok(context.getSingleResponseUserProfileDto());
    }

    @Override
    public ResponseEntity<LoginResponseDto> login(@RequestBody(required = false) LoginRequestDto loginRequestDto) {
        AccountContext<LoginRequestDto> context = AccountContext.<LoginRequestDto>builder()
                .task(loginRequestDto).build();
        loginService.execute(context);
        return ResponseEntity.ok(context.getLoginResponseDto());
    }
}
