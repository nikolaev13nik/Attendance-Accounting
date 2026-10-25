package co.il.attendanceaccounting.service.base;

import co.il.attendanceaccounting.context.AccountContext;

public interface BaseService<T> {

    void execute(AccountContext<T> context);

    default void executeWithoutTransactional(AccountContext<T> context) {
    }
}
