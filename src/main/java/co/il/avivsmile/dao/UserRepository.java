package co.il.avivsmile.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import co.il.avivsmile.model.User;

public interface UserRepository extends JpaRepository<User, Integer>{

}
