package co.il.avivsmile.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.ManyToAny;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "hours")
@EqualsAndHashCode(of = "id")
public class DataTime {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	Integer id;
//	Integer idUser;
	@JsonFormat(pattern="yyyy-MM-dd")
	LocalDate date;
	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
	LocalDateTime start;
	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
	LocalDateTime finish;
	
	
	@ManyToOne
	@JoinColumn(name = "idUser")
	User user;
	
	
	
	
	public DataTime(User user, LocalDate date, LocalDateTime start, LocalDateTime finish) {
		this.user = user;
		this.date = date;
		this.start = start;
		this.finish = finish;
	}
	
	
	
}
