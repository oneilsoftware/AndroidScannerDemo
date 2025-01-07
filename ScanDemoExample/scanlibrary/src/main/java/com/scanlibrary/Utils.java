package com.scanlibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by jhansi on 05/04/15.
 */
public class Utils {

    private Utils() {

    }

    public static Uri getUri(Context context, Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, bytes);
        Log.d("ScanLibraryUtils", "getUri: " + bitmap.getByteCount());
        byte[] byteArray = bytes.toByteArray();
        Log.d("ScanLibraryUtils", "new bytes: " + byteArray.length);

        Bitmap newBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), newBitmap, "Title", null);
        return Uri.parse(path);
    }

    public static Bitmap getBitmap(Context context, Uri uri) throws IOException {
        Log.d("ScanLibraryUtils", "getBitmap: " + uri.toString());
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        return bitmap;
    }
}