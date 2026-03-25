package kursovaya.executor;

// интерфейс политики отказа
public interface RejectedTaskHandler {
    void onRejected(Runnable task, CustomThreadPool pool);
}
