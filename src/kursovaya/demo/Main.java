package kursovaya.demo;

import kursovaya.executor.CustomThreadPool;
import kursovaya.executor.RejectedHandlers;

import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("CUSTOM THREAD POOL DEMO\n");

        CustomThreadPool pool = new CustomThreadPool(
                2,  // corePoolSize
                4,  // maxPoolSize
                1,  // minSpareThreads
                5,  // keepAliveTime
                TimeUnit.SECONDS,
                5,  // queueSize
                RejectedHandlers.callerRunsPolicy()
        );

        System.out.println("\nОТПРАВКА ОБЫЧНЫХ ЗАДАЧ:");
        for (int i = 1; i <= 10; i++) {
            int id = i;
            pool.execute(makeTask(id, 1000));
        }

        Thread.sleep(2000);

        System.out.println("\nПРОВЕРКА МЕТОДА SUBMIT:");
        Future<Integer> future = pool.submit(() -> {
            System.out.println("[Callable] started");
            Thread.sleep(500);
            System.out.println("[Callable] finished");
            return 42;
        });
        System.out.println("Result from submit: " + future.get());

        System.out.println("\nТЕСТИРОВАНИЕ ПЕРЕГРУЗКИ:");

        CustomThreadPool overloadPool = new CustomThreadPool(
                2, 3,
                0,
                4, TimeUnit.SECONDS,
                3,
                RejectedHandlers.abortPolicy()
        );

        int rejected = 0;
        for (int i = 11; i <= 30; i++) {
            try {
                overloadPool.execute(makeTask(i, 3000));
            } catch (RejectedExecutionException e) {
                rejected++;
            }
        }
        System.out.println("Rejected by caller: " + rejected);

        Thread.sleep(3000);
        overloadPool.shutdown();
        overloadPool.awaitTermination(15, TimeUnit.SECONDS);

        System.out.println("\nЗАВЕРШЕНИЕ РАБОТЫ ОСНОВНОГО ПУЛА...");
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        System.out.println("\nПРОГРАММА ЗАВЕРШЕНА");
    }

    // задача которая спит и логирует себя
    private static Runnable makeTask(int id, long sleepMs) {
        return new Runnable() {
            @Override
            public void run() {
                System.out.println("[Task] #" + id + " started in " + Thread.currentThread().getName());
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[Task] #" + id + " interrupted");
                    return;
                }
                System.out.println("[Task] #" + id + " finished");
            }

            @Override
            public String toString() {
                return "DemoTask-" + id;
            }
        };
    }
}
