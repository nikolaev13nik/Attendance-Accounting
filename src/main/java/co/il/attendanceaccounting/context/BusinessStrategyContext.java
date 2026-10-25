package co.il.attendanceaccounting.context;

import java.util.function.Consumer;

public interface BusinessStrategyContext {

    default <T extends BusinessStrategyContext> Consumer<T> getPostServiceAction() {
        return (businessStrategyContext) -> {
        };
    }

}
