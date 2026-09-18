package co.il.attendanceaccounting.model;

import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

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
	Set<String> roles;

	public boolean addRole(String role) {
		return roles.add(role);
	}

	public boolean removeRole(String role) {
		return roles.remove(role);
	}

}