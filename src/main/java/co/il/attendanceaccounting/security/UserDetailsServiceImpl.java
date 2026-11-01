package co.il.attendanceaccounting.security;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;




@Service
public class UserDetailsServiceImpl implements UserDetailsService{
	
	@Autowired
	UserRepository userRepository;

	private static final String PRINCIPAL_SEPARATOR = ":";

	public static String principalOf(Integer tenantId, Integer idUser) {
		return tenantId + PRINCIPAL_SEPARATOR + idUser;
	}

	@Override
	public UserDetails loadUserByUsername(@NonNull String principal) {
		User user = findByPrincipal(principal).orElseThrow(() -> new UsernameNotFoundException(principal));
		String password = user.getPassword();
		return new org.springframework.security.core.userdetails.User(String.valueOf(user.getIdUser()), password,
				AuthorityUtils.createAuthorityList(user.getRoles()
				.stream()
				.map(String::toUpperCase).distinct().toArray(String[]::new)));
	}

	private Optional<User> findByPrincipal(String principal) {
		String[] parts = principal.split(PRINCIPAL_SEPARATOR, 2);
		try {
			return userRepository.findById(new UserBasicKey(Integer.valueOf(parts[0]), Integer.valueOf(parts[1])));
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			return Optional.empty();
		}
	}

}
