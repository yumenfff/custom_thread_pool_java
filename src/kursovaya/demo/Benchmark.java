package kursovaya.demo;

import kursovaya.executor.CustomThreadPool;

import java.util.concurrent.*;

// сравнение моего пула с JDK
public class Benchmark {

    private final boolean enableLogs;

    public Benchmark() {
        // для честного замера в бенчмарке логи выключены
        this(false);
    }

    public Benchmark(boolean enableLogs) {
        this.enableLogs = enableLogs;
    }

    public static void main(String[] args) throws Exception {
        new Benchmark().run();
    }

    public void run() throws Exception {
        int tasks = 2000;
        int threads = 4;

        System.out.println("BENCHMARK: CustomThreadPool vs JDK");
        System.out.println("Tasks: " + tasks + ", Threads: " + threads);

        long customTime = runCustomPool(tasks, threads);
        System.out.println("Custom: " + customTime + " ms");
        Thread.sleep(500);
        long jdkTime = runJdkPool(tasks, threads);

        System.out.println("\nRESULTS:");
        System.out.println("CustomThreadPool : " + customTime + " ms");
        System.out.println("JDK ThreadPool : " + jdkTime + " ms");

        long diff = customTime - jdkTime;
        double percent = ((double) diff / jdkTime) * 100;
        System.out.printf("Difference : %+d ms (%.1f%%)%n", diff, percent);

        if (Math.abs(percent) < 15) {
            System.out.println("-> Производительность сопоставима");
        } else if (diff > 0) {
            System.out.println("-> JDK пул быстрее");
        } else {
            System.out.println("-> Мой пул быстрее");
        }
    }

    private long runCustomPool(int tasks, int threads) throws Exception {
        CustomThreadPool pool = new CustomThreadPool(
                threads, threads,
                1,
                1, TimeUnit.MILLISECONDS,
                tasks,
                enableLogs
        );

        CountDownLatch latch = new CountDownLatch(tasks);
        long start = System.nanoTime();

        for (int i = 0; i < tasks; i++) {
            pool.execute(() -> {
                dummyWork();
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return elapsed;
    }


    private static long runJdkPool(int tasks, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(tasks);
        long start = System.nanoTime();

        for (int i = 0; i < tasks; i++) {
            pool.execute(() -> {
                dummyWork();
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("JDK : " + elapsed + " ms");
        return elapsed;
    }

    private static void dummyWork() {
        long x = 0;
        for (int i = 0; i < 500; i++) x += i;
        if (x < 0) System.out.println("never");
    }
}
