package co.il.attendanceaccounting.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;

public interface UserRepository extends JpaRepository<User, UserBasicKey>{

	List<User> findByUserBasicKeyTenantId(Integer tenantId);

	boolean existsByUserBasicKeyTenantId(Integer tenantId);


	@Query("select count(distinct u) from User u join u.roles r where u.userBasicKey.tenantId = :tenantId and upper(r) = :role")
	long countByTenantIdAndRole(@Param("tenantId") Integer tenantId, @Param("role") String role);

}
