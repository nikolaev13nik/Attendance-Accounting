package co.il.attendanceaccounting.dao;



import org.springframework.data.jpa.repository.JpaRepository;
import co.il.attendanceaccounting.model.DataTime;

public interface UserTimeRepository extends JpaRepository<DataTime, Integer>{
	

}
