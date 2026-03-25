package kursovaya.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements CustomExecutor {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int minSpareThreads;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final RejectedTaskHandler rejectedTaskHandler;
    private final boolean loggingEnabled;

    // у каждого воркера своя очередь
    private final List<Worker> workers = new ArrayList<>();
    private final LoggingThreadFactory threadFactory;

    // счётчик для Round Robin (по кругу выбирает воркера)
    private final AtomicInteger rrCounter = new AtomicInteger();

    private volatile boolean isShutdown = false;

    public CustomThreadPool(int corePoolSize, int maxPoolSize,
                            int minSpareThreads,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize) {
        this(corePoolSize, maxPoolSize, minSpareThreads,
                keepAliveTime, timeUnit, queueSize,
                RejectedHandlers.callerRunsPolicy(), true);
    }

    public CustomThreadPool(int corePoolSize, int maxPoolSize,
                            int minSpareThreads,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize,
                            boolean loggingEnabled) {
        this(corePoolSize, maxPoolSize, minSpareThreads,
                keepAliveTime, timeUnit, queueSize,
                RejectedHandlers.callerRunsPolicy(), loggingEnabled);
    }

    public CustomThreadPool(int corePoolSize, int maxPoolSize,
                            int minSpareThreads,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize,
                            RejectedTaskHandler rejectedTaskHandler) {
        this(corePoolSize, maxPoolSize, minSpareThreads,
                keepAliveTime, timeUnit, queueSize,
                rejectedTaskHandler, true);
    }

    public CustomThreadPool(int corePoolSize, int maxPoolSize,
                            int minSpareThreads,
                            long keepAliveTime, TimeUnit timeUnit,
                            int queueSize,
                            RejectedTaskHandler rejectedTaskHandler,
                            boolean loggingEnabled) {

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.minSpareThreads = minSpareThreads;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.rejectedTaskHandler = rejectedTaskHandler;
        this.loggingEnabled = loggingEnabled;
        this.threadFactory = new LoggingThreadFactory("MyPool", loggingEnabled);

        // базовые потоки
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    private synchronized Worker addWorker() {
        Worker worker = new Worker();
        workers.add(worker);
        worker.thread.start();
        return worker;
    }

    @Override
    public void execute(Runnable command) {
        // если пул уже завершён, то отклоняю задачу
        if (isShutdown) {
            throw new RejectedExecutionException("[Rejected] Pool is shutdown");
        }

        synchronized (this) {
            if (getIdleCountLocked() < minSpareThreads && workers.size() < maxPoolSize) {
                addWorker();
            }
        }

        if (!tryEnqueue(command)) {
            synchronized (this) {
                if (workers.size() < maxPoolSize) {
                    Worker newWorker = addWorker();
                    if (!newWorker.queue.offer(command)) {
                        rejectedTaskHandler.onRejected(command, this);
                    } else {
                        log("[Pool] Task accepted into queue #" + newWorker.id + ": " + command);
                    }
                } else {
                    rejectedTaskHandler.onRejected(command, this);
                }
            }
        }
    }

    // Round Robin (раскидывает задачи по очередям воркеров по кругу)
    private synchronized boolean tryEnqueue(Runnable command) {
        if (workers.isEmpty()) return false;

        int index = Math.floorMod(rrCounter.getAndIncrement(), workers.size());
        Worker target = workers.get(index);
        if (target.queue.offer(command)) {
            log("[Pool] Task accepted into queue #" + target.id + ": " + command);
            return true;
        }

        for (Worker w : workers) {
            if (w.queue.offer(command)) {
                log("[Pool] Task accepted into queue #" + w.id + ": " + command);
                return true;
            }
        }

        return false;
    }

    private int getIdleCountLocked() {
        int idle = 0;
        for (Worker w : workers) {
            if (w.idle) idle++;
        }
        return idle;
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        execute(task);
        return task;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log("[Pool] Shutdown started");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        synchronized (this) {
            for (Worker w : workers) {
                w.thread.interrupt();
                w.queue.clear();
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (this) {
            while (!workers.isEmpty() && System.currentTimeMillis() < deadline) {
                wait(100);
            }
        }
        if (!workers.isEmpty()) {
            log("[Pool] Not all tasks completed in time. Active workers: " + workers.size());
            return false;
        } else {
            log("[Pool] All tasks completed successfully");
            return true;
        }
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    private void log(String message) {
        if (loggingEnabled) {
            System.out.println(message);
        }
    }

    private class Worker implements Runnable {

        private static final AtomicInteger idSeq = new AtomicInteger();

        final int id = idSeq.incrementAndGet();
        final Thread thread;
        final BlockingQueue<Runnable> queue;
        volatile boolean idle = true;

        Worker() {
            this.queue = new ArrayBlockingQueue<>(queueSize);
            this.thread = threadFactory.newThread(this);
        }

        @Override
        public void run() {
            try {
                while (!isShutdown || !queue.isEmpty()) {

                    idle = true;
                    Runnable task = queue.poll(keepAliveTime, timeUnit);
                    idle = false;

                    if (task == null) {
                        synchronized (CustomThreadPool.this) {
                            if (workers.size() > corePoolSize) {
                                log("[Worker] " + thread.getName() + " idle timeout, stopping");
                                break;
                            }
                        }
                        continue;
                    }

                    // проверяю что пул не завершается перед выполнением
                    if (isShutdown && queue.isEmpty()) {
                        break;
                    }

                    log("[Worker] " + thread.getName() + " executes " + task);
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                synchronized (CustomThreadPool.this) {
                    workers.remove(this);
                    if (isShutdown && workers.isEmpty()) {
                        CustomThreadPool.this.notifyAll();
                    }
                    log("[Worker] " + thread.getName() + " terminated");
                }
            }
        }
    }
}
