package co.il.attendanceaccounting.service;

import java.util.List;
import java.util.Set;

import co.il.attendanceaccounting.dto.UserDto;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.UserRegisterDto;


public interface UserAccountService {
	
	UserProfileDto register(UserRegisterDto userRegisterDto);
	
	UserProfileDto login(Integer idUser);
	
	UserProfileDto editUser(Integer idUser, UserEditDto userEditDto);
	
	UserProfileDto removeUser(Integer idUser);
	
	boolean changePassword(Integer idUser, String password);

	UserProfileDto addRole(Integer idUser, String role);

	UserProfileDto removeRole(Integer idUser, String role);

	List<UserProfileDto>getAllUsers();
}
