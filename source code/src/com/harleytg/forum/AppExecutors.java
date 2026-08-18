package com.harleytg.forum.dev;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Shared process-wide executors so routine work does not create a new thread per request. */
final class AppExecutors {
    private static final int CPU_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int NETWORK_THREADS = Math.max(2, Math.min(4, CPU_COUNT));

    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(NETWORK_THREADS, r -> {
        Thread t = new Thread(r, "hcf-network");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hcf-disk");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hcf-serial");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "hcf-scheduler");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static ExecutorService network() { return NETWORK; }
    static ExecutorService disk() { return DISK; }
    static ExecutorService serial() { return SERIAL; }
    static ScheduledExecutorService scheduler() { return SCHEDULER; }
    static Handler main() { return MAIN; }

    private AppExecutors() {}
}
