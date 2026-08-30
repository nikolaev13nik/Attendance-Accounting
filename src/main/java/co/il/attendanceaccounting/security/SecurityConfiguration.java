package co.il.attendanceaccounting.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import static co.il.attendanceaccounting.security.SecurityConstants.AUTHORITIES;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	private final String ADMINISTRATOR_ROLE="ADMINISTRATOR";

	@Bean
	SecretKeySpec jwtSecretKey(@Value("${attendance-accounting.security.jwt.secret}") String secret){
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length <32){
			throw new IllegalStateException(
					"attendance-accounting.security.jwt.secret must be at least 32 bytes for HS256");

		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	WebExpressionAuthorizationManager.Builder authz() {
		return WebExpressionAuthorizationManager.withDefaults();
	}

	@Bean
	WebSecurityCustomizer webSecurityCustomizer() {
		return web -> web.ignoring().requestMatchers(PathRequest.toH2Console());
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
	JwtAuthenticationConverter jwtAuthenticationConverter(){
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName(AUTHORITIES);
		authoritiesConverter.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder){
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return  new ProviderManager(provider);
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, WebExpressionAuthorizationManager.Builder authz,
									JwtAuthenticationConverter jwtAuthenticationConverter) {
		http.oauth2ResourceServer(oauth2 ->oauth2.jwt(jwt ->jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
		http.exceptionHandling(ex->ex.accessDeniedHandler(new AccessDeniedHandlerImpl()));

		http.csrf(csrf -> csrf.disable());
		http.cors(cors -> cors.disable());
		http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/record/**").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.PUT, "/record/start/{idUser}")
						.access(authz.expression("@customWebSecurity.checkStartFinishRecords(#idUser, authentication) or hasRole('ADMINISTRATOR')"))
				.requestMatchers(HttpMethod.PUT, "/record/finish/{idUser}")
						.access(authz.expression("@customWebSecurity.checkStartFinishRecords(#idUser, authentication) or hasRole('ADMINISTRATOR')"))
				.requestMatchers(HttpMethod.POST, "/record").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.DELETE, "/record/remove/{id}").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.DELETE, "/account/user/{idUser}").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.POST, "/account/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/account/user/{idUser}/role/{role}").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.DELETE, "/account/user/{idUser}/role/{role}").hasRole(ADMINISTRATOR_ROLE)
				.requestMatchers(HttpMethod.PUT, "/account/user/password/{idUser}")
						.access(authz.expression("@customWebSecurity.checkAuthorityChangePassword(#idUser, authentication) or hasRole('ADMINISTRATOR')"))
				.anyRequest().permitAll()
		);

		return http.build();
	}

}