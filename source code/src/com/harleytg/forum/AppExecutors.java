package com.harleytg.forum;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/** Shared HCF v10000072 executor pool. */
final class AppExecutors {
    private static final int CPU_COUNT;
    private static final ExecutorService DISK;
    private static final Handler MAIN;
    private static final ExecutorService NETWORK;
    private static final int NETWORK_THREADS;
    private static final ScheduledExecutorService SCHEDULER;
    private static final ExecutorService SERIAL;

    static {
        int max = Math.max(2, Runtime.getRuntime().availableProcessors());
        CPU_COUNT = max;
        int max2 = Math.max(2, Math.min(4, max));
        NETWORK_THREADS = max2;
        NETWORK = Executors.newFixedThreadPool(max2, runnable -> {
            Thread thread = new Thread(runnable, "hcf-network");
            thread.setPriority(5);
            return thread;
        });
        DISK = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hcf-disk");
            thread.setPriority(4);
            return thread;
        });
        SERIAL = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hcf-serial");
            thread.setPriority(5);
            return thread;
        });
        SCHEDULER = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "hcf-scheduler");
            thread.setPriority(5);
            return thread;
        });
        MAIN = new Handler(Looper.getMainLooper());
    }

    static ExecutorService network() { return NETWORK; }
    static ExecutorService disk() { return DISK; }
    static ExecutorService serial() { return SERIAL; }
    static ScheduledExecutorService scheduler() { return SCHEDULER; }
    static Handler main() { return MAIN; }

    private AppExecutors() {}
}
