package co.il.attendanceaccounting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@ToString
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "att_ac_tenant_mapping")
@EqualsAndHashCode(of = "tenantId")
public class TenantMapping {

	@Id
	private Integer tenantId;
	private String tenantName;

	@Column(length = 15)
	private String status;

	private String email;

}
