package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;
import co.il.attendanceaccounting.service.base.DataTimeServiceBase;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.model.DataTime;
import jakarta.persistence.TypedQuery;
import java.util.List;

@Service
public class GetRecordsByDayService extends DataTimeServiceBase<List<DataTimeDto>> {

    @Override
    protected void fetchAndValidate(DataTimeContext<List<DataTimeDto>> context) {
        TypedQuery<DataTime> query = em.createQuery(
            "select h from DataTime h where h.date=?1 ORDER BY id", 
            DataTime.class
        );
        query.setParameter(1, context.getDate());
        context.setDataTimeList(query.getResultList());
    }

    @Override
    protected void mapResult(DataTimeContext<List<DataTimeDto>> context) {
        context.setResult(mapper.toDtoList(context.getDataTimeList()));
    }
}