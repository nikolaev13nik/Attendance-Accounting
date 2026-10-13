package co.il.attendanceaccounting.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import co.il.attendanceaccounting.model.TenantMapping;

public interface TenantMappingRepository extends JpaRepository<TenantMapping, Integer> {

	boolean existsByCompanyNameIgnoreCase(String companyName);

	Optional<TenantMapping> findByCompanyNameIgnoreCase(String companyName);

}
