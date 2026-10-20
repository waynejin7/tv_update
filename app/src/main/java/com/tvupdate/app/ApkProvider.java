package com.tvupdate.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "com.tvupdate.app.apks";

    static Uri uriFor(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(file.getName()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1)
            throw new FileNotFoundException("Invalid APK URI");
        String name = uri.getLastPathSegment();
        if (!name.matches("[A-Za-z0-9_-]+\\.apk"))
            throw new FileNotFoundException("Invalid APK name");
        File file = new File(requireContext().getFilesDir(), "apks/" + name);
        if (!file.isFile()) throw new FileNotFoundException("APK not found");
        return file;
    }

    private android.content.Context requireContext() {
        android.content.Context context = getContext();
        if (context == null) throw new IllegalStateException("No context");
        return context;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{"_display_name", "_size"} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if ("_display_name".equals(columns[i])) row[i] = file.getName();
                if ("_size".equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
