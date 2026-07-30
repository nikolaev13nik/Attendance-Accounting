package co.il.avivsmile.security;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import co.il.avivsmile.dao.UserRepository;
import co.il.avivsmile.model.User;




@Service
public class UserDetailsServiceImpl implements UserDetailsService{
	
	@Autowired
	UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(@NonNull String id) {
		
		User user = userRepository.findById(Integer.parseInt(id))
								.orElseThrow(()->new UsernameNotFoundException(id));
		String password = user.getPassword();
		return new org.springframework.security.core.userdetails.User(id, password, AuthorityUtils.createAuthorityList(user.getRoles()
				.stream()
				.map(String::toUpperCase).distinct().toArray(String[]::new)));
	}

}
