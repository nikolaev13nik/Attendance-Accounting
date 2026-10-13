package co.il.attendanceaccounting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer tenantId;

	@Column(name = "company_name", nullable = false, unique = true)
	private String companyName;

	@Column(name = "contact_name", nullable = false)
	private String contactName;

	@Column(nullable = false, length = 40)
	private String phone;

	@Column(nullable = false)
	private String email;

	@Column(length = 15)
	private String status;

}
