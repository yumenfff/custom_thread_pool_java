package kursovaya.executor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingThreadFactory implements ThreadFactory {

    private final String poolName;
    private final boolean loggingEnabled;
    private final AtomicInteger counter = new AtomicInteger();

    public LoggingThreadFactory(String poolName, boolean loggingEnabled) {
        this.poolName = poolName;
        this.loggingEnabled = loggingEnabled;
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = poolName + "-worker-" + counter.incrementAndGet();
        if (loggingEnabled) {
            System.out.println("[ThreadFactory] Creating new thread: " + name);
        }
        return new Thread(r, name);
    }
}
