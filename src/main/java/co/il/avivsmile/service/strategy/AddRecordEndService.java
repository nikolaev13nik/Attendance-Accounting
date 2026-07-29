package co.il.avivsmile.service.strategy;

import org.springframework.stereotype.Service;
import co.il.avivsmile.service.base.DataTimeServiceBase;
import co.il.avivsmile.dto.DataTimeDto;
import co.il.avivsmile.context.DataTimeContext;
import co.il.avivsmile.model.DataTime;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddRecordEndService extends DataTimeServiceBase<DataTimeDto> {

    @Override
    protected void fetchAndValidate(DataTimeContext<DataTimeDto> context) {
        super.fetchAndValidate(context);
        TypedQuery<DataTime> query = em.createQuery(
                "select h from DataTime h where h.user.idUser = ?1 and h.date = ?2 ORDER BY id DESC",
                DataTime.class
        );
        query.setParameter(1, context.getIdUser());
        query.setParameter(2, LocalDate.now());
        context.setDataTimeList(query.getResultList());
    }

    @Override
    protected void executeBusiness(DataTimeContext<DataTimeDto> context) {
        List<DataTime> list = context.getDataTimeList();
        if (!list.isEmpty() && list.get(0).getFinish() == null) {
            DataTime dataTime = list.get(0);
            dataTime.setFinish(LocalDateTime.now());
            context.setDataTime(dataTime);
        } else {
            context.setDataTime(new DataTime(context.getUser(), LocalDate.now(), null, LocalDateTime.now()));
        }
    }

    @Override
    protected void persist(DataTimeContext<DataTimeDto> context) {
        if (context.getDataTime().getId() == null) {
            context.setDataTime(timeRepository.save(context.getDataTime()));
        }
    }

    @Override
    protected void mapResult(DataTimeContext<DataTimeDto> context) {
        context.setResult(mapper.toDto(context.getDataTime()));
    }
}