package co.il.attendanceaccounting.service;

import co.il.attendanceaccounting.dto.TenantAddDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;

public interface TenantService {

	TenantMappingDto addTenant(TenantAddDto tenantAddDto);

	boolean tenantExists(Integer tenantId);

	String getCompanyName(Integer tenantId);

	Integer resolveTenantId(String companyName);
}
