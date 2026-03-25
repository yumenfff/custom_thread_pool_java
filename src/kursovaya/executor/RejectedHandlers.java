package kursovaya.executor;

import java.util.concurrent.RejectedExecutionException;

// две готовые политики отказа
public final class RejectedHandlers {

    private RejectedHandlers() {
    }

    public static RejectedTaskHandler abortPolicy() {
        return (task, pool) -> {
            if (pool.isLoggingEnabled()) {
                System.out.println("[Rejected] Task " + task + " was rejected due to overload!");
            }
            throw new RejectedExecutionException("Task rejected: " + task);
        };
    }

    public static RejectedTaskHandler callerRunsPolicy() {
        return (task, pool) -> {
            if (pool.isLoggingEnabled()) {
                System.out.println("[Rejected] Pool overloaded, running in caller thread: " + task);
            }
            if (!pool.isShutdown()) {
                task.run();
            }
        };
    }
}
