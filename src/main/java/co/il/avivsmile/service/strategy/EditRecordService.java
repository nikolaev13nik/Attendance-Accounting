package co.il.avivsmile.service.strategy;

import org.springframework.stereotype.Service;
import co.il.avivsmile.service.base.DataTimeServiceBase;
import co.il.avivsmile.dto.DataTimeDto;
import co.il.avivsmile.dto.EditDataTimeUserDto;
import co.il.avivsmile.context.DataTimeContext;
import co.il.avivsmile.model.DataTime;

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