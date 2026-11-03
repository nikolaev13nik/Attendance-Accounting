package co.il.attendanceaccounting.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import co.il.attendanceaccounting.configuration.AccountConfiguration;
import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	SecretKeySpec jwtSecretKey(AccountConfiguration accountConfiguration){
		byte[] keyBytes = accountConfiguration.getJwtSecret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length <32){
			throw new IllegalStateException(
					"attendance-accounting.security.jwt.secret must be at least 32 bytes for HS256");

		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKeySpec jwtSecretKey){
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKeySpec jwtSecretKey){
		return  NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build() ;
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder){
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return  new ProviderManager(provider);
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
									 AccessDeniedHandler accessDeniedHandler,
									 AuthenticationEntryPoint authenticationEntryPoint) {
		http.oauth2ResourceServer(oauth2 ->oauth2
				.authenticationEntryPoint(authenticationEntryPoint)
				.jwt(jwt ->jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
		http.exceptionHandling(ex->ex.accessDeniedHandler(accessDeniedHandler));

		http.csrf(csrf -> csrf.disable());
		http.cors(cors -> cors.disable());
		http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http.authorizeHttpRequests(auth -> auth
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/account/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/account/signup").permitAll()
				.anyRequest().authenticated()
		);

		return http.build();
	}

}