package co.il.attendanceaccounting.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class UserBasicKey implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "tenant_id", nullable = false)
	private Integer tenantId;

	@Column(name = "id_user", nullable = false)
	private Integer idUser;

}
