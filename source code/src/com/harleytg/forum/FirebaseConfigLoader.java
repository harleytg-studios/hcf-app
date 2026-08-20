package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.harleytg.forum.FirebaseConfigLoader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
final class FirebaseConfigLoader {

    interface Callback {
        void onResult(Config config, String str);
    }

    static final class Config {
        final String apiKey;
        final String appId;
        final String authDomain;
        final String measurementId;
        final String messagingSenderId;
        final String projectId;
        final String source;
        final String storageBucket;

        Config(String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8) {
            this.apiKey = str;
            this.authDomain = str2;
            this.projectId = str3;
            this.storageBucket = str4;
            this.messagingSenderId = str5;
            this.appId = str6;
            this.measurementId = str7;
            this.source = str8;
        }

        boolean isValid() {
            return FirebaseConfigLoader.notBlank(this.apiKey) && FirebaseConfigLoader.notBlank(this.projectId) && FirebaseConfigLoader.notBlank(this.messagingSenderId) && FirebaseConfigLoader.notBlank(this.appId);
        }

        String safeSummary() {
            if (!isValid()) {
                return "Configuration unavailable";
            }
            return "Loaded • project " + this.projectId + " • " + this.source;
        }
    }

    static Config load(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        Config parse = parse(sharedPreferences.getString("firebase_config_cache", ""), sharedPreferences.getString("firebase_config_source", "Cached HTTPS config"));
        if (parse != null && parse.isValid()) {
            return parse;
        }
        try {
            Config parse2 = parse(readAll(context.getAssets().open("firebase-config.js")), "Bundled firebase-config.js");
            if (parse2 == null) {
                return null;
            }
            if (parse2.isValid()) {
                return parse2;
            }
            return null;
        } catch (Throwable unused) {
            return null;
        }
    }

    static void refresh(Context context, final Callback callback) {
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        final String trim = sharedPreferences.getString("firebase_config_url", "").trim();
        if (trim.isEmpty()) {
            Config load = load(applicationContext);
            callback.onResult(load, load == null ? "Bundled Firebase config could not be read." : "Using bundled Firebase config.");
        } else if (!trim.startsWith("https://")) {
            callback.onResult(load(applicationContext), "Firebase config URL must use HTTPS.");
        } else {
            AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.FirebaseConfigLoader$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    FirebaseConfigLoader.lambda$refresh$1(trim, sharedPreferences, applicationContext, callback);
                }
            });
        }
    }

    static /* synthetic */ void lambda$refresh$1(String str, SharedPreferences sharedPreferences, Context context, final Callback callback) {
        final Config config;
        final String str2;
        HttpURLConnection httpURLConnection;
        int responseCode;
        HttpURLConnection httpURLConnection2 = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            try {
                httpURLConnection.setConnectTimeout(8000);
                httpURLConnection.setReadTimeout(8000);
                httpURLConnection.setInstanceFollowRedirects(true);
                httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 FirebaseConfig");
                responseCode = httpURLConnection.getResponseCode();
            } catch (Throwable th) {
                th = th;
                httpURLConnection2 = httpURLConnection;
                try {
                    Config load = load(context);
                    StringBuilder sb = new StringBuilder("Remote refresh failed; kept ");
                    sb.append(load == null ? "no config" : load.source);
                    sb.append(".");
                    String sb2 = sb.toString();
                    AppLogger.error(context, "firebase_config_refresh", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                    config = load;
                    str2 = sb2;
                    new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.harleytg.forum.FirebaseConfigLoader$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            FirebaseConfigLoader.Callback.this.onResult(config, str2);
                        }
                    });
                } finally {
                    if (httpURLConnection2 != null) {
                        httpURLConnection2.disconnect();
                    }
                }
            }
        } catch (Throwable th2) {
            th = th2;
        }
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("HTTP " + responseCode);
        }
        String readAll = readAll(httpURLConnection.getInputStream());
        config = parse(readAll, "HTTPS config");
        if (config == null || !config.isValid()) {
            throw new IllegalStateException("Invalid Firebase config");
        }
        sharedPreferences.edit().putString("firebase_config_cache", readAll).putString("firebase_config_source", "HTTPS config").apply();
        str2 = "Firebase config refreshed from HTTPS.";
        if (httpURLConnection != null) {
            httpURLConnection.disconnect();
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.harleytg.forum.FirebaseConfigLoader$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                FirebaseConfigLoader.Callback.this.onResult(config, str2);
            }
        });
    }

    static void clearRemoteCache(Context context) {
        context.getSharedPreferences("hcf_app", 0).edit().remove("firebase_config_cache").remove("firebase_config_source").apply();
    }

    private static Config parse(String str, String str2) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        Config config = new Config(value(str, "apiKey"), value(str, "authDomain"), value(str, "projectId"), value(str, "storageBucket"), value(str, "messagingSenderId"), value(str, "appId"), value(str, "measurementId"), str2);
        if (config.isValid()) {
            return config;
        }
        return null;
    }

    private static String value(String str, String str2) {
        Matcher matcher = Pattern.compile("(?:\\\"?" + Pattern.quote(str2) + "\\\"?)\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']").matcher(str);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean notBlank(String str) {
        return (str == null || str.trim().isEmpty()) ? false : true;
    }

    private static String readAll(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                return sb.toString();
            }
            sb.append(readLine);
            sb.append('\n');
        }
    }

    private FirebaseConfigLoader() {
    }
}
