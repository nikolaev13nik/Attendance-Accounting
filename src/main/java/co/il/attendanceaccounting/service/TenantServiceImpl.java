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
		String companyName = tenantAddDto.getCompanyName().trim();
		if (tenantMappingRepository.existsByCompanyNameIgnoreCase(companyName)) {
			throw new TenantExistsException();
		}
		TenantMapping tenantMapping = TenantMapping.builder()
				.companyName(companyName)
				.contactName(tenantAddDto.getContactName().trim())
				.phone(tenantAddDto.getPhone().trim())
				.email(tenantAddDto.getEmail().trim())
				.build();
		tenantMappingRepository.save(tenantMapping);
		return tenantMappingToTenantMappingDto(tenantMapping);
	}

	@Override
	public boolean tenantExists(Integer tenantId) {
		return tenantId != null && tenantMappingRepository.existsById(tenantId);
	}

	@Override
	public String getCompanyName(Integer tenantId) {
		if (tenantId == null) {
			return null;
		}
		return tenantMappingRepository.findById(tenantId)
				.map(TenantMapping::getCompanyName)
				.orElse(null);
	}

	@Override
	public Integer resolveTenantId(String companyName) {
		if (companyName == null || companyName.isBlank()) {
			return null;
		}
		return tenantMappingRepository.findByCompanyNameIgnoreCase(companyName.trim())
				.map(TenantMapping::getTenantId)
				.orElse(null);
	}

	private TenantMappingDto tenantMappingToTenantMappingDto(TenantMapping tenantMapping) {
		return TenantMappingDto.builder()
				.tenantId(tenantMapping.getTenantId())
				.companyName(tenantMapping.getCompanyName())
				.contactName(tenantMapping.getContactName())
				.phone(tenantMapping.getPhone())
				.email(tenantMapping.getEmail())
				.status(tenantMapping.getStatus())
				.build();
	}
}
