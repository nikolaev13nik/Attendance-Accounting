package co.il.avivsmile.service.base;

import co.il.avivsmile.context.DataTimeContext;

public interface BaseService<R> {

    void execute(DataTimeContext<R> context);

    // use this when you want to run the pipeline with your own transaction boundary
    default void executeWithoutTransactional(DataTimeContext<R> context) {
    }
}