package co.il.attendanceaccounting;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.model.User;
import lombok.extern.slf4j.Slf4j;

import static co.il.attendanceaccounting.security.SecurityConstants.SYSTEM_TENANT_ID;
import static co.il.attendanceaccounting.security.SecurityConstants.SecurityRoles.PLATFORM_ADMIN;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Slf4j
@SpringBootApplication
public class AttendanceAccountingApplication implements CommandLineRunner{

	@Autowired
	UserRepository accountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;
	@Value("${attendance-accounting.bootstrap.admin.id:123456789}")
	private int bootstrapAdminId;
	@Value("${attendance-accounting.bootstrap.admin.password:}")
	private String bootstrapAdminPassword;


	public static void main(String[] args) {
		SpringApplication.run(AttendanceAccountingApplication.class, args);
	}


	@Override
	public void run(String... args) throws Exception {

		if (!StringUtils.hasText(bootstrapAdminPassword)) {
			throw new IllegalStateException(
					"No bootstrap password was supplied. Set "
							+ "ATTENDANCE_ACCOUNTING_BOOTSTRAP_ADMIN_PASSWORD (from a Secret, "
							+ "at least 12 characters).");
		}

		Optional<User> existing = accountRepository.findById(bootstrapAdminId);
		if (existing.isEmpty()) {
			String hashPassword = passwordEncoder.encode(bootstrapAdminPassword);
			User admin = User.builder().idUser(bootstrapAdminId).password(hashPassword).firstName("Platform")
					.lastName("Admin")
					.tenantId(SYSTEM_TENANT_ID)
					.roles(platformRoles()).build();
			accountRepository.save(admin);
			log.info("Created platform admin {} in tenant {}.", bootstrapAdminId, SYSTEM_TENANT_ID);
			return;
		}
		User admin = existing.get();
		if (isFalse(platformRoles().equals(admin.getRoles())) || isFalse(SYSTEM_TENANT_ID.equals(admin.getTenantId()))) {
			admin.setRoles(platformRoles());
			admin.setTenantId(SYSTEM_TENANT_ID);
			accountRepository.save(admin);
			log.info("Reset platform admin {} to roles {} in tenant {}.", bootstrapAdminId, admin.getRoles(),
					SYSTEM_TENANT_ID);
		}
	}

	private static Set<String> platformRoles() {
		return new HashSet<>(Set.of(PLATFORM_ADMIN.name()));
	}

}