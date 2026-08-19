package com.harleytg.forum.dev;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class UpdateFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.harleytg.forum.dev.updatefiles";

    static Uri uriForFile(Context context, File file) throws IOException {
        if (context == null || file == null) throw new IOException("Missing update file");
        File verified = verifiedFile(context, file.getName());
        if (!verified.getCanonicalFile().equals(file.getCanonicalFile())) {
            throw new IOException("Update file is outside the updater directory");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(verified.getName())
                .build();
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) { return AppUpdateDownloader.APK_MIME; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null) return null;
        try {
            File file = fileForUri(context, uri);
            String[] cols = projection == null || projection.length == 0
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                    : projection;
            MatrixCursor cursor = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.length();
                else row[i] = null;
            }
            cursor.addRow(row);
            return cursor;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only update provider");
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Context unavailable");
        try {
            File file = fileForUri(context, uri);
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Throwable t) {
            throw new FileNotFoundException("Update APK unavailable");
        }
    }

    private static File fileForUri(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority())) {
            throw new IOException("Invalid update URI");
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) throw new IOException("Missing update filename");
        return verifiedFile(context, name);
    }

    private static File verifiedFile(Context context, String name) throws IOException {
        String safe = name == null ? "" : name.trim();
        if (safe.isEmpty() || safe.contains("/") || safe.contains("\\") || safe.contains("..")
                || !safe.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            throw new IOException("Invalid update filename");
        }
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) throw new IOException("Updater directory unavailable");
        File file = new File(dir, safe).getCanonicalFile();
        File canonicalDir = dir.getCanonicalFile();
        if (!canonicalDir.equals(file.getParentFile()) || !file.isFile()) {
            throw new IOException("Update file not found");
        }
        return file;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
