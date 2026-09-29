package co.il.attendanceaccounting;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.security.SecurityConstants;
import lombok.extern.slf4j.Slf4j;

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

		if (isFalse(accountRepository.existsById(bootstrapAdminId))) {
			String hashPassword = passwordEncoder.encode(bootstrapAdminPassword);
			User admin = User.builder().idUser(bootstrapAdminId).password(hashPassword).firstName("Super")
					.lastName("Admin")
					.tenantId(SecurityConstants.SYSTEM_TENANT_ID)
					.roles(Arrays.stream(SecurityConstants.SecurityRoles.values()).map(Enum::name)
							.collect(Collectors.toSet())).build();
			accountRepository.save(admin);
			log.info("Created super-admin {} in tenant {}.", bootstrapAdminId,
					SecurityConstants.SYSTEM_TENANT_ID);
		}
	}

}