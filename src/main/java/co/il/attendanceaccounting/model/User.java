package co.il.attendanceaccounting.model;

import java.util.Set;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.*;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@ToString
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
@EqualsAndHashCode(of = "userBasicKey")
public class User {

	@EmbeddedId
	private UserBasicKey userBasicKey;
	private String firstName;
	private String lastName;
	private String password;
	private String email;

	@Singular
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = {
			@JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id"),
			@JoinColumn(name = "user_id", referencedColumnName = "id_user")})
	Set<String> roles;

	public Integer getIdUser() {
		return userBasicKey.getIdUser();
	}

	public Integer getTenantId() {
		return userBasicKey.getTenantId();
	}

	public boolean hasRole(String role) {
		return roles.stream().anyMatch(role::equalsIgnoreCase);
	}

	public boolean addRole(String role) {
		return isFalse(hasRole(role)) && roles.add(role);
	}

	public void removeRole(String role) {
		 roles.removeIf(role::equalsIgnoreCase);
	}

}