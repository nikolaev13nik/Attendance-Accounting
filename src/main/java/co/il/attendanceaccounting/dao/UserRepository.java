package co.il.attendanceaccounting.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import co.il.attendanceaccounting.model.User;

public interface UserRepository extends JpaRepository<User, Integer>{

}
