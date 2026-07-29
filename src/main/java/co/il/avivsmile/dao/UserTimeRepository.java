package co.il.avivsmile.dao;



import org.springframework.data.jpa.repository.JpaRepository;
import co.il.avivsmile.model.DataTime;

public interface UserTimeRepository extends JpaRepository<DataTime, Integer>{
	

}
