package co.il.avivsmile.service.strategy;

import org.springframework.stereotype.Service;
import co.il.avivsmile.service.base.DataTimeServiceBase;
import co.il.avivsmile.dto.DataTimeDto;
import co.il.avivsmile.context.DataTimeContext;

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