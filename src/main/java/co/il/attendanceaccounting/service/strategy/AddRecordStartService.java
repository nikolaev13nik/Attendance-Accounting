package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;
import co.il.attendanceaccounting.service.base.DataTimeServiceBase;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.model.DataTime;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AddRecordStartService extends DataTimeServiceBase<DataTimeDto> {

    @Override
    protected void executeBusiness(DataTimeContext<DataTimeDto> context) {
        context.setDataTime(new DataTime(context.getUser(), LocalDate.now(), LocalDateTime.now(), null));
    }

    @Override
    protected void persist(DataTimeContext<DataTimeDto> context) {
        context.setDataTime(timeRepository.save(context.getDataTime()));
    }

    @Override
    protected void mapResult(DataTimeContext<DataTimeDto> context) {
        context.setResult(mapper.toDto(context.getDataTime()));
    }
}