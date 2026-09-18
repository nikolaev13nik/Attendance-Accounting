package co.il.attendanceaccounting.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import co.il.attendanceaccounting.dao.UserRepository;


@Component("customWebSecurity")
public class CustomWebSecurity {
	
	@Autowired
	UserRepository userRepository;
	
	public boolean checkAuthorityChangePassword(Integer idUser, Authentication authentication) {
		return idUser.equals(Integer.parseInt(authentication.getName()));
	}
	
}
