package com.harleytg.forum;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/* loaded from: classes.dex */
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
        NETWORK = Executors.newFixedThreadPool(max2, new ThreadFactory() { // from class: com.harleytg.forum.AppExecutors$$ExternalSyntheticLambda0
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$0(runnable);
            }
        });
        DISK = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: com.harleytg.forum.AppExecutors$$ExternalSyntheticLambda1
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$1(runnable);
            }
        });
        SERIAL = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: com.harleytg.forum.AppExecutors$$ExternalSyntheticLambda2
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$2(runnable);
            }
        });
        SCHEDULER = Executors.newScheduledThreadPool(2, new ThreadFactory() { // from class: com.harleytg.forum.AppExecutors$$ExternalSyntheticLambda3
            @Override // java.util.concurrent.ThreadFactory
            public final Thread newThread(Runnable runnable) {
                return AppExecutors.lambda$static$3(runnable);
            }
        });
        MAIN = new Handler(Looper.getMainLooper());
    }

    static /* synthetic */ Thread lambda$static$0(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-network");
        thread.setPriority(5);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$1(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-disk");
        thread.setPriority(4);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$2(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-serial");
        thread.setPriority(5);
        return thread;
    }

    static /* synthetic */ Thread lambda$static$3(Runnable runnable) {
        Thread thread = new Thread(runnable, "hcf-scheduler");
        thread.setPriority(5);
        return thread;
    }

    static ExecutorService network() {
        return NETWORK;
    }

    static ExecutorService disk() {
        return DISK;
    }

    static ExecutorService serial() {
        return SERIAL;
    }

    static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }

    static Handler main() {
        return MAIN;
    }

    private AppExecutors() {
    }
}
