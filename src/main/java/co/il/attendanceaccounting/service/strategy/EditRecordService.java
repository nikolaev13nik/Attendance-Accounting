package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;
import co.il.attendanceaccounting.service.base.DataTimeServiceBase;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.dto.EditDataTimeUserDto;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.model.DataTime;

@Service
public class EditRecordService extends DataTimeServiceBase<DataTimeDto> {

    @Override
    protected void fetchAndValidate(DataTimeContext<DataTimeDto> context) {
        context.setDataTime(findRecordOrThrow(context.getEditDto().getId()));
    }

    @Override
    protected void executeBusiness(DataTimeContext<DataTimeDto> context) {
        EditDataTimeUserDto editDto = context.getEditDto();
        DataTime dataTime = context.getDataTime();
        
        if (editDto.getStart() != null) {
            dataTime.setStart(editDto.getStart());
        }
        
        if (editDto.getFinish() != null) {
            dataTime.setFinish(editDto.getFinish());
        }
    }

    @Override
    protected void mapResult(DataTimeContext<DataTimeDto> context) {
        context.setResult(mapper.toDto(context.getDataTime()));
    }
}