package co.il.attendanceaccounting.service.base;

import org.springframework.beans.factory.annotation.Autowired;
import co.il.attendanceaccounting.context.DataTimeContext;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.dao.UserTimeRepository;
import co.il.attendanceaccounting.exceptions.RecordNotFoundException;
import co.il.attendanceaccounting.exceptions.UserNotFoundException;
import co.il.attendanceaccounting.mapper.DataTimeMapper;
import co.il.attendanceaccounting.model.DataTime;
import co.il.attendanceaccounting.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

public abstract class DataTimeServiceBase<R> implements BaseService<R> {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserTimeRepository timeRepository;

    @Autowired
    protected DataTimeMapper mapper;

    @PersistenceContext
    protected EntityManager em;

    @Transactional
    @Override
    public void execute(DataTimeContext<R> context) {
        executeWithoutTransactional(context);
    }

    @Override
    public void executeWithoutTransactional(DataTimeContext<R> context) {
        fetchAndValidate(context);
        executeBusiness(context);
        persist(context);
        mapResult(context);
    }

    protected void fetchAndValidate(DataTimeContext<R> context) {
        context.setUser(findUserOrThrow(context.getIdUser()));
    }

    protected void executeBusiness(DataTimeContext<R> context) {
    }

    protected void persist(DataTimeContext<R> context) {
    }

    protected void mapResult(DataTimeContext<R> context) {
    }

    protected User findUserOrThrow(Integer idUser) {
        return userRepository.findById(idUser)
                .orElseThrow(() -> new UserNotFoundException(idUser.toString()));
    }

    protected DataTime findRecordOrThrow(Integer id) {
        return timeRepository.findById(id).orElseThrow(RecordNotFoundException::new);
    }
}