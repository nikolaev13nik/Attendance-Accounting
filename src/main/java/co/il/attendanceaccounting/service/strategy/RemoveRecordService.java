package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;
import co.il.attendanceaccounting.service.base.DataTimeServiceBase;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.context.DataTimeContext;

@Service
public class RemoveRecordService extends DataTimeServiceBase<DataTimeDto> {

    @Override
    protected void fetchAndValidate(DataTimeContext<DataTimeDto> context) {
        context.setDataTime(findRecordOrThrow(context.getRecordId()));
    }

    @Override
    protected void persist(DataTimeContext<DataTimeDto> context) {
        timeRepository.delete(context.getDataTime());
    }

    @Override
    protected void mapResult(DataTimeContext<DataTimeDto> context) {
        context.setResult(mapper.toDto(context.getDataTime()));
    }
}