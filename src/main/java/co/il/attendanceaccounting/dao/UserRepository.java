package co.il.attendanceaccounting.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import co.il.attendanceaccounting.model.User;

public interface UserRepository extends JpaRepository<User, Integer>{

	List<User> findByTenantId(Integer tenantId);

	boolean existsByTenantId(Integer tenantId);

	Optional<User> findByIdUserAndTenantId(Integer idUser, Integer tenantId);

}
