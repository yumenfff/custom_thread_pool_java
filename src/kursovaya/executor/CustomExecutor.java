package kursovaya.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

// интерфейс пула
public interface CustomExecutor extends Executor {

    // запуск Runnable задачи
    @Override
    void execute(Runnable command);

    // запуск Callable задачи с результатом
    <T> Future<T> submit(Callable<T> callable);

    // мягкое завершение
    void shutdown();

    // резкое завершение
    void shutdownNow();
}
