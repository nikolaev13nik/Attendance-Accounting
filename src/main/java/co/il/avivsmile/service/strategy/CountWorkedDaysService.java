package co.il.avivsmile.service.strategy;

import org.springframework.stereotype.Service;
import co.il.avivsmile.service.base.DataTimeServiceBase;
import co.il.avivsmile.context.DataTimeContext;
import jakarta.persistence.TypedQuery;

@Service
public class CountWorkedDaysService extends DataTimeServiceBase<Long> {

    @Override
    protected void fetchAndValidate(DataTimeContext<Long> context) {
        super.fetchAndValidate(context);
        TypedQuery<Long> query = em.createQuery(
            "select COUNT(distinct date) from DataTime h where h.user.idUser = ?1 and date BETWEEN :start and :finish",
            Long.class
        );
        query.setParameter(1, context.getIdUser());
        query.setParameter("start", context.getStartDate());
        query.setParameter("finish", context.getFinishDate());
        context.setResult(query.getResultList().get(0));
    }
}