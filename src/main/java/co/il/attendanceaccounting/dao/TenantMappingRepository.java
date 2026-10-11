package co.il.attendanceaccounting.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import co.il.attendanceaccounting.model.TenantMapping;

public interface TenantMappingRepository extends JpaRepository<TenantMapping, Integer> {

}
