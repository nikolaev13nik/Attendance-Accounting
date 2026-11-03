package co.il.attendanceaccounting.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.Getter;

@Getter
@Configuration
public class AccountConfiguration {

	@Value("${attendance-accounting.bootstrap.admin.id:123456789}")
	private int bootstrapAdminId;

	@Value("${attendance-accounting.bootstrap.admin.password:}")
	private String bootstrapAdminPassword;

	@Value("${attendance-accounting.security.jwt.secret}")
	private String jwtSecret;

	@Value("${attendance-accounting.security.jwt.ttl-seconds:3600}")
	private long jwtTtlSeconds;

	@Value("${attendance-accounting.captcha.secret}")
	private String captchaSecret;

	@Value("${attendance-accounting.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
	private String captchaVerifyUrl;

	@Bean
	public PasswordEncoder getPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
