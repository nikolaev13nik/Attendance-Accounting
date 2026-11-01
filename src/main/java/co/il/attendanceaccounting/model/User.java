package co.il.attendanceaccounting.model;

import java.util.Set;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
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
@EqualsAndHashCode(of = "idUser")
public class User {

	@Id
	private Integer idUser;
	private String firstName;
	private String lastName;
	private String password;
	private String email;

	@Column(nullable = false)
	private Integer tenantId;

	@Singular
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	Set<String> roles;

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