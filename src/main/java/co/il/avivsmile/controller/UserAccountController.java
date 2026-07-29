package co.il.avivsmile.controller;

import java.util.List;

import co.il.avivsmile.security.AuthenticationService;
import co.il.avivsmile.security.dto.LoginRequestDto;
import co.il.avivsmile.security.dto.LoginResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import co.il.avivsmile.api.AccountApi;
import co.il.avivsmile.dto.UserEditDto;
import co.il.avivsmile.dto.UserProfileDto;
import co.il.avivsmile.dto.UserRegisterDto;
import co.il.avivsmile.service.UserAccountService;

@RestController
public class UserAccountController implements AccountApi {

	@Autowired
	private  UserAccountService userAccountService;
	@Autowired
    private AuthenticationService authenticationService;

	@Override
	public ResponseEntity<UserProfileDto> register(UserRegisterDto userRegisterDto) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userAccountService.register(userRegisterDto));
	}

	@Override
	public ResponseEntity<UserProfileDto> removeUser(Integer idUser) {
		return ResponseEntity.ok(userAccountService.removeUser(idUser));
	}

	@Override
	public ResponseEntity<UserProfileDto> editUser(Integer idUser, UserEditDto userEditDto) {
		return ResponseEntity.ok(userAccountService.editUser(idUser, userEditDto));
	}

	@Override
	public ResponseEntity<UserProfileDto> addRole(Integer idUser, String role) {
		return ResponseEntity.ok(userAccountService.addRole(idUser, role));
	}

	@Override
	public ResponseEntity<UserProfileDto> removeRole(Integer idUser, String role) {
		return ResponseEntity.ok(userAccountService.removeRole(idUser, role));
	}

	@Override
	public ResponseEntity<List<UserProfileDto>> getAllUsers() {
		return ResponseEntity.ok(userAccountService.getAllUsers());
	}

	@PostMapping("/account/login")
	public ResponseEntity<LoginResponseDto> login(@RequestBody(required = false)LoginRequestDto request) {
		LoginResponseDto response = authenticationService.authenticate(request);
		response.setProfile(userAccountService.login(request.idUser()));
		return ResponseEntity.ok(response);
	}
}