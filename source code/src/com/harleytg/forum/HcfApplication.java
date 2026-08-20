package com.harleytg.forum;

import android.app.Application;
import android.os.Build;
import java.lang.Thread;

/* loaded from: classes.dex */
public final class HcfApplication extends Application {
    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        RuntimeState.install(this);
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { // from class: com.harleytg.forum.HcfApplication$$ExternalSyntheticLambda2
            @Override // java.lang.Thread.UncaughtExceptionHandler
            public final void uncaughtException(Thread thread, Throwable th) {
                HcfApplication.this.m6lambda$onCreate$0$comharleytgforumdevHcfApplication(defaultUncaughtExceptionHandler, thread, th);
            }
        });
        try {
            UiPreferences.migrate(this);
        } catch (Throwable unused) {
        }
        try {
            AppLogger.info(this, "app_start", "1.0 | SDK " + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL);
        } catch (Throwable unused2) {
        }
        AppExecutors.main().postDelayed(new Runnable() { // from class: com.harleytg.forum.HcfApplication$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                HcfApplication.this.m9lambda$onCreate$3$comharleytgforumdevHcfApplication();
            }
        }, 3500L);
    }

    /* renamed from: lambda$onCreate$0$com-harleytg-forum-dev-HcfApplication, reason: not valid java name */
    /* synthetic */ void m6lambda$onCreate$0$comharleytgforumdevHcfApplication(Thread.UncaughtExceptionHandler uncaughtExceptionHandler, Thread thread, Throwable th) {
        try {
            TelemetryService.captureCrash(this, thread, th);
        } catch (Throwable unused) {
        }
        try {
            AppLogger.crash(this, th);
        } catch (Throwable unused2) {
        }
        if (uncaughtExceptionHandler != null) {
            uncaughtExceptionHandler.uncaughtException(thread, th);
        }
    }

    /* renamed from: lambda$onCreate$3$com-harleytg-forum-dev-HcfApplication, reason: not valid java name */
    /* synthetic */ void m9lambda$onCreate$3$comharleytgforumdevHcfApplication() {
        AppExecutors.disk().execute(new Runnable() { // from class: com.harleytg.forum.HcfApplication$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                HcfApplication.this.m7lambda$onCreate$1$comharleytgforumdevHcfApplication();
            }
        });
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.HcfApplication$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                HcfApplication.this.m8lambda$onCreate$2$comharleytgforumdevHcfApplication();
            }
        });
    }

    /* renamed from: lambda$onCreate$1$com-harleytg-forum-dev-HcfApplication, reason: not valid java name */
    /* synthetic */ void m7lambda$onCreate$1$comharleytgforumdevHcfApplication() {
        try {
            AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(this);
        } catch (Throwable unused) {
        }
    }

    /* renamed from: lambda$onCreate$2$com-harleytg-forum-dev-HcfApplication, reason: not valid java name */
    /* synthetic */ void m8lambda$onCreate$2$comharleytgforumdevHcfApplication() {
        try {
            TelemetryService.heartbeat(this);
        } catch (Throwable unused) {
        }
    }

    @Override // android.app.Application, android.content.ComponentCallbacks2
    public void onTrimMemory(int i) {
        super.onTrimMemory(i);
        RuntimeState.noteTrimMemory(i);
    }

    @Override // android.app.Application, android.content.ComponentCallbacks
    public void onLowMemory() {
        super.onLowMemory();
        RuntimeState.noteTrimMemory(80);
    }
}
