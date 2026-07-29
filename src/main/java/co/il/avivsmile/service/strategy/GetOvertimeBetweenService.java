package co.il.avivsmile.service.strategy;

import org.springframework.stereotype.Service;
import co.il.avivsmile.service.base.DataTimeServiceBase;
import co.il.avivsmile.context.DataTimeContext;
import jakarta.persistence.Query;

@Service
public class GetOvertimeBetweenService extends DataTimeServiceBase<Long> {

    @Override
    protected void fetchAndValidate(DataTimeContext<Long> context) {
        super.fetchAndValidate(context);
        Query query = em.createNativeQuery(
            "select SUM(DATEDIFF(MINUTE, h.start, h.finish) - 480) from hours h where h.id_user=?1 and (h.date BETWEEN ?2 and ?3) and DATEDIFF(MINUTE, h.start, h.finish) > 480"
        );
        query.setParameter(1, context.getIdUser());
        query.setParameter(2, context.getStartDate());
        query.setParameter(3, context.getFinishDate());
        
        Object result = query.getSingleResult();
        context.setResult(result == null ? null : ((Number) result).longValue());
    }
}