package co.il.attendanceaccounting.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.dao.TenantMappingRepository;
import co.il.attendanceaccounting.dto.TenantAddDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.exceptions.TenantExistsException;
import co.il.attendanceaccounting.model.TenantMapping;

@Service
public class TenantServiceImpl implements TenantService {

	@Autowired
	TenantMappingRepository tenantMappingRepository;

	@Override
	public TenantMappingDto addTenant(TenantAddDto tenantAddDto) {
		if (tenantMappingRepository.existsById(tenantAddDto.getTenantId())) {
			throw new TenantExistsException();
		}
		TenantMapping tenantMapping = TenantMapping.builder()
				.tenantId(tenantAddDto.getTenantId())
				.tenantName(tenantAddDto.getTenantName())
				.email(tenantAddDto.getEmail())
				.build();
		tenantMappingRepository.save(tenantMapping);
		return tenantMappingToTenantMappingDto(tenantMapping);
	}

	@Override
	public boolean tenantExists(Integer tenantId) {
		return tenantId != null && tenantMappingRepository.existsById(tenantId);
	}

	private TenantMappingDto tenantMappingToTenantMappingDto(TenantMapping tenantMapping) {
		return TenantMappingDto.builder()
				.tenantId(tenantMapping.getTenantId())
				.tenantName(tenantMapping.getTenantName())
				.status(tenantMapping.getStatus())
				.email(tenantMapping.getEmail())
				.build();
	}
}
