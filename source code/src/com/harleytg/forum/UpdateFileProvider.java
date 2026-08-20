package com.harleytg.forum;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/* loaded from: classes.dex */
public final class UpdateFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.harleytg.forum.updatefiles";

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String str, String[] strArr) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        return 0;
    }

    static Uri uriForFile(Context context, File file) throws IOException {
        if (context == null || file == null) {
            throw new IOException("Missing update file");
        }
        File verifiedFile = verifiedFile(context, file.getName());
        if (!verifiedFile.getCanonicalFile().equals(file.getCanonicalFile())) {
            throw new IOException("Update file is outside the updater directory");
        }
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(verifiedFile.getName()).build();
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        try {
            File fileForUri = fileForUri(context, uri);
            if (strArr == null || strArr.length == 0) {
                strArr = new String[]{"_display_name", "_size"};
            }
            MatrixCursor matrixCursor = new MatrixCursor(strArr, 1);
            Object[] objArr = new Object[strArr.length];
            for (int i = 0; i < strArr.length; i++) {
                if ("_display_name".equals(strArr[i])) {
                    objArr[i] = fileForUri.getName();
                } else if ("_size".equals(strArr[i])) {
                    objArr[i] = Long.valueOf(fileForUri.length());
                } else {
                    objArr[i] = null;
                }
            }
            matrixCursor.addRow(objArr);
            return matrixCursor;
        } catch (Throwable unused) {
            return null;
        }
    }

    @Override // android.content.ContentProvider
    public ParcelFileDescriptor openFile(Uri uri, String str) throws FileNotFoundException {
        if (!"r".equals(str)) {
            throw new FileNotFoundException("Read-only update provider");
        }
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Context unavailable");
        }
        return ParcelFileDescriptor.open(fileForUri(context, uri), 268435456);
    }

    private static File fileForUri(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())) {
            throw new IOException("Invalid update URI");
        }
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null || lastPathSegment.trim().isEmpty()) {
            throw new IOException("Missing update filename");
        }
        return verifiedFile(context, lastPathSegment);
    }

    private static File verifiedFile(Context context, String str) throws IOException {
        String trim = str == null ? "" : str.trim();
        if (trim.isEmpty() || trim.contains("/") || trim.contains("\\") || trim.contains("..") || !trim.toLowerCase(Locale.US).endsWith(".apk")) {
            throw new IOException("Invalid update filename");
        }
        File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (externalFilesDir == null) {
            throw new IOException("Updater directory unavailable");
        }
        File canonicalFile = new File(externalFilesDir, trim).getCanonicalFile();
        if (externalFilesDir.getCanonicalFile().equals(canonicalFile.getParentFile()) && canonicalFile.isFile()) {
            return canonicalFile;
        }
        throw new IOException("Update file not found");
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues contentValues) {
        throw new UnsupportedOperationException("Read only");
    }
}
