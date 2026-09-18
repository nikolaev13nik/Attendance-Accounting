package co.il.attendanceaccounting.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.il.attendanceaccounting.model.User;

public interface UserRepository extends JpaRepository<User, Integer>{

	List<User> findByTenantId(Integer tenantId);

}
