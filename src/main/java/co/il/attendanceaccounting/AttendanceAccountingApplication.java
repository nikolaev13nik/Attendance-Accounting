package co.il.attendanceaccounting;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import co.il.attendanceaccounting.configuration.AccountConfiguration;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
import lombok.extern.slf4j.Slf4j;

import static co.il.attendanceaccounting.security.SecurityConstants.PLATFORM_TENANT_ID;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.PLATFORM_ADMIN;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Slf4j
@SpringBootApplication
public class AttendanceAccountingApplication implements CommandLineRunner{

	@Autowired
	UserRepository accountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AccountConfiguration accountConfiguration;


	public static void main(String[] args) {
		SpringApplication.run(AttendanceAccountingApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {
		int bootstrapAdminId = accountConfiguration.getBootstrapAdminId();
		String bootstrapAdminPassword = accountConfiguration.getBootstrapAdminPassword();

		if (!StringUtils.hasText(bootstrapAdminPassword)) {
			throw new IllegalStateException(
					"No bootstrap password was supplied. Set "
							+ "ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD (from a Secret, "
							+ "at least 12 characters).");
		}

		Optional<User> existing = accountRepository.findById(new UserBasicKey(PLATFORM_TENANT_ID, bootstrapAdminId));
		if (existing.isEmpty()) {
			String hashPassword = passwordEncoder.encode(bootstrapAdminPassword);
			User admin = User.builder().userBasicKey(new UserBasicKey(PLATFORM_TENANT_ID, bootstrapAdminId))
					.password(hashPassword).firstName("Platform")
					.lastName("Admin")
					.roles(platformRoles()).build();
			accountRepository.save(admin);
			log.info("Created platform admin {} in tenant {}.", bootstrapAdminId, PLATFORM_TENANT_ID);
			return;
		}
		User admin = existing.get();
		if (isFalse(platformRoles().equals(admin.getRoles()))) {
			admin.setRoles(platformRoles());
			accountRepository.save(admin);
			log.info("Reset platform admin {} to roles {} in tenant {}.", bootstrapAdminId, admin.getRoles(),
					PLATFORM_TENANT_ID);
		}
	}

	private static Set<String> platformRoles() {
		return new HashSet<>(Set.of(PLATFORM_ADMIN.name()));
	}

}