package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;
import co.il.attendanceaccounting.service.base.DataTimeServiceBase;
import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.model.DataTime;
import jakarta.persistence.TypedQuery;
import java.util.List;

@Service
public class GetRecordsByMonthService extends DataTimeServiceBase<List<DataTimeDto>> {

    @Override
    protected void fetchAndValidate(DataTimeContext<List<DataTimeDto>> context) {
        super.fetchAndValidate(context);
        TypedQuery<DataTime> query = em.createQuery(
            "select h from DataTime h where h.user.idUser=?1 and date BETWEEN :start and :finish",
            DataTime.class
        );
        query.setParameter(1, context.getIdUser());
        query.setParameter("start", context.getStartDate());
        query.setParameter("finish", context.getFinishDate());
        context.setDataTimeList(query.getResultList());
    }

    @Override
    protected void mapResult(DataTimeContext<List<DataTimeDto>> context) {
        context.setResult(mapper.toDtoList(context.getDataTimeList()));
    }
}