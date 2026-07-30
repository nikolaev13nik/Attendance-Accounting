package co.il.avivsmile;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.stream.Collectors;

import co.il.avivsmile.dao.UserRepository;
import co.il.avivsmile.model.User;
import co.il.avivsmile.security.SecurityConstants;

@SpringBootApplication
public class AvivSmileApplication implements CommandLineRunner{
	
	@Autowired
	UserRepository accountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	
	public static void main(String[] args) {
		SpringApplication.run(AvivSmileApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {
		if (!accountRepository.existsById(123456789)) {
			String hashPassword = passwordEncoder.encode("admin");
			User admin = User.builder().idUser(123456789).password(hashPassword).firstName("Super")
					.lastName("Admin")
					.roles(Arrays.stream(SecurityConstants.SecurityRoles.values()).map(Enum::name)
							.collect(Collectors.toSet())).build();
			accountRepository.save(admin);
		}
	}

}