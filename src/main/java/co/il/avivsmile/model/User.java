package co.il.avivsmile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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

	@OneToMany(mappedBy = "user")
	private List<DataTime> records;

	@Singular
	@ElementCollection(fetch = FetchType.EAGER)
	Set<String> roles;

	public boolean addRole(String role) {
		return roles.add(role);
	}

	public boolean removeRole(String role) {
		return roles.remove(role);
	}

	public User(Integer idUser, String firstName, String lastName, String password, Set<String> roles) {
		this.idUser = idUser;
		this.firstName = firstName;
		this.lastName = lastName;
		this.password = password;
		this.roles = roles;
		this.records = new ArrayList<>();
	}
}