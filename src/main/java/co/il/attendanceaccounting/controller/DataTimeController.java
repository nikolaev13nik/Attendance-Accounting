package co.il.attendanceaccounting.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.dto.EditDataTimeUserDto;
import co.il.attendanceaccounting.service.strategy.AddRecordEndService;
import co.il.attendanceaccounting.service.strategy.AddRecordStartService;
import co.il.attendanceaccounting.service.strategy.CheckNullRowsService;
import co.il.attendanceaccounting.service.strategy.CountWorkedDaysService;
import co.il.attendanceaccounting.service.strategy.EditRecordService;
import co.il.attendanceaccounting.service.strategy.GetHoursBetweenService;
import co.il.attendanceaccounting.service.strategy.GetRecordsByDayService;
import co.il.attendanceaccounting.service.strategy.GetRecordsByMonthService;
import co.il.attendanceaccounting.service.strategy.GetOvertimeBetweenService;
import co.il.attendanceaccounting.service.strategy.RemoveRecordService;
import lombok.RequiredArgsConstructor;

@RequestMapping("/record")
@RestController
@RequiredArgsConstructor
public class DataTimeController {

	private final AddRecordStartService addRecordStartService;
	private final AddRecordEndService addRecordEndService;
	private final EditRecordService editRecordService;
	private final GetRecordsByDayService getRecordsByDayService;
	private final CountWorkedDaysService countWorkedDaysService;
	private final GetRecordsByMonthService getRecordsByMonthService;
	private final RemoveRecordService removeRecordService;
	private final GetHoursBetweenService getHoursBetweenService;
	private final GetOvertimeBetweenService getOvertimeBetweenService;
	private final CheckNullRowsService checkNullRowsService;

	@PutMapping("/start/{idUser}")
	public ResponseEntity<DataTimeDto> addRecordStart(@PathVariable Integer idUser) {
		DataTimeContext<DataTimeDto> context = DataTimeContext.<DataTimeDto>builder()
				.idUser(idUser).build();
		addRecordStartService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@PutMapping("/finish/{idUser}")
	public ResponseEntity<DataTimeDto> addRecordEnd(@PathVariable Integer idUser) {
		DataTimeContext<DataTimeDto> context = DataTimeContext.<DataTimeDto>builder()
				.idUser(idUser).build();
		addRecordEndService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@PostMapping()
	public ResponseEntity<DataTimeDto> editRecord(@RequestBody EditDataTimeUserDto dataTimeDto) {
		DataTimeContext<DataTimeDto> context = DataTimeContext.<DataTimeDto>builder()
				.editDto(dataTimeDto).build();
		editRecordService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/records")
	public ResponseEntity<List<DataTimeDto>> getAllRecordsByDay(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate localDate) {
		DataTimeContext<List<DataTimeDto>> context = DataTimeContext.<List<DataTimeDto>>builder()
				.date(localDate).build();
		getRecordsByDayService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/range/records")
	public ResponseEntity<List<DataTimeDto>> getAllRecordsEmployeeByMonth(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
	                                                                      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate finishDate,
	                                                                      @RequestParam Integer idUser) {
		DataTimeContext<List<DataTimeDto>> context = DataTimeContext.<List<DataTimeDto>>builder()
				.idUser(idUser).startDate(startDate).finishDate(finishDate).build();
		getRecordsByMonthService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/workdays")
	public ResponseEntity<Long> getCountWorkedDaysByEmployee(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
	                                                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate finishDate,
	                                                         @RequestParam Integer idUser) {
		DataTimeContext<Long> context = DataTimeContext.<Long>builder()
				.idUser(idUser).startDate(startDate).finishDate(finishDate).build();
		countWorkedDaysService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/hours")
	public ResponseEntity<Long> getAllHoursEmployeeBetweenDates(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
	                                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate finishDate,
	                                                            @RequestParam Integer idUser) {
		DataTimeContext<Long> context = DataTimeContext.<Long>builder()
				.idUser(idUser).startDate(startDate).finishDate(finishDate).build();
		getHoursBetweenService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/overtime")
	public ResponseEntity<Long> getOvertimeEmployeeBetweenDates(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
	                                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate finishDate,
	                                                            @RequestParam Integer idUser) {
		DataTimeContext<Long> context = DataTimeContext.<Long>builder()
				.idUser(idUser).startDate(startDate).finishDate(finishDate).build();
		getOvertimeBetweenService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@GetMapping("/check")
	public ResponseEntity<List<DataTimeDto>> checkRowsForNull(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
	                                                          @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate finishDate,
	                                                          @RequestParam Integer idUser) {
		DataTimeContext<List<DataTimeDto>> context = DataTimeContext.<List<DataTimeDto>>builder()
				.idUser(idUser).startDate(startDate).finishDate(finishDate).build();
		checkNullRowsService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}

	@DeleteMapping("/remove/{id}")
	public ResponseEntity<DataTimeDto> removeRecord(@PathVariable Integer id) {
		DataTimeContext<DataTimeDto> context = DataTimeContext.<DataTimeDto>builder()
				.recordId(id).build();
		removeRecordService.execute(context);
		return ResponseEntity.ok(context.getResult());
	}
}