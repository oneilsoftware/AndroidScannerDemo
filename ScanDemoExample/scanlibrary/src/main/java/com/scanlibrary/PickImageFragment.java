package com.scanlibrary;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by jhansi on 04/04/15.
 */
public class PickImageFragment extends Fragment {

    private View view;
    private ImageButton cameraButton;
    private ImageButton galleryButton;
    private Uri fileUri;
    private IScanner scanner;
    private ProgressDialogFragment progressDialogFragment;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof IScanner)) {
            throw new ClassCastException("Activity must implement IScanner");
        }
        this.scanner = (IScanner) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.pick_image_fragment, null);
        init();
        return view;
    }

    private void init() {
        cameraButton = (ImageButton) view.findViewById(R.id.cameraButton);
        cameraButton.setOnClickListener(new CameraButtonClickListener());
        galleryButton = (ImageButton) view.findViewById(R.id.selectButton);
        galleryButton.setOnClickListener(new GalleryClickListener());
        if (isIntentPreferenceSet()) {
            handleIntentPreference();
        } else {
            getActivity().finish();
        }
    }

    private void clearTempImages() {
        try {
            File tempFolder = new File(getActivity().getExternalFilesDir(null).getPath());
            for (File f : tempFolder.listFiles())
                f.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleIntentPreference() {
        int preference = getIntentPreference();
        if (preference == ScanConstants.OPEN_CAMERA) {
            openCamera();
        } else if (preference == ScanConstants.OPEN_MEDIA) {
            openMediaContent();
        }
    }

    private boolean isIntentPreferenceSet() {
        int preference = getArguments().getInt(ScanConstants.OPEN_INTENT_PREFERENCE, 0);
        return preference != 0;
    }

    private int getIntentPreference() {
        int preference = getArguments().getInt(ScanConstants.OPEN_INTENT_PREFERENCE, 0);
        return preference;
    }


    private class CameraButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            openCamera();
        }
    }

    private class GalleryClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            openMediaContent();
        }
    }

    public void openMediaContent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, ScanConstants.PICKFILE_REQUEST_CODE);
    }

    public void openCamera() {

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            File file = createImageFile();
            boolean isDirectoryCreated = file.getParentFile().mkdirs();
            Log.d("ScanLibraryUtils", "openCamera: isDirectoryCreated: " + isDirectoryCreated);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri tempFileUri = FileProvider.getUriForFile(getActivity().getApplicationContext(),
                        "com.oneilsoft.oneilmobile.provider", // As defined in Manifest
                        file);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempFileUri);
            } else {
                Uri tempFileUri = Uri.fromFile(file);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempFileUri);
            }
            startActivityForResult(cameraIntent, ScanConstants.START_CAMERA_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
    }

    private File createImageFile() {
        clearTempImages();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new
                Date());
        File file = new File(getActivity().getExternalFilesDir(null), "IMG_" + timeStamp +
                ".jpg");
        fileUri = Uri.fromFile(file);
        return file;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("ScanLibraryUtils", "onActivityResult" + resultCode);
        Bitmap bitmap = null;
        if (resultCode == Activity.RESULT_OK) {
            try {
                switch (requestCode) {
                    case ScanConstants.START_CAMERA_REQUEST_CODE:
                        bitmap = getBitmap(fileUri);
                        break;

                    case ScanConstants.PICKFILE_REQUEST_CODE:
                        bitmap = getBitmap(data.getData());
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getActivity().finish();
        }
        if (bitmap != null) {
            postImagePick(bitmap);
        }
    }

    protected void postImagePick(Bitmap bitmap) {
        Uri uri = Utils.getUri(getActivity(), bitmap);
        bitmap.recycle();
        scanner.onBitmapSelect(uri);
    }

    private Bitmap getBitmap(Uri selectedimg) throws IOException {
        Log.d("ScanLibraryUtils", "pick getBitmap start");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        AssetFileDescriptor fileDescriptor = null;
        fileDescriptor =
                getActivity().getContentResolver().openAssetFileDescriptor(selectedimg, "r");
        Matrix matrix = new Matrix();
        ExifInterface exif = null;
        int rotation = 0;
        try {
            exif = new ExifInterface(selectedimg.getPath());
            rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotationInDegrees = exifToDegrees(rotation);
            if (rotation != 0) {
                matrix.preRotate(rotationInDegrees);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Bitmap original
                = BitmapFactory.decodeFileDescriptor(
                fileDescriptor.getFileDescriptor(), null, options);
        Log.d("ScanLibraryUtils", String.format("pick getBitmap start C h/w: %d, %d", original.getWidth(), original.getHeight()));

        int width = original.getWidth();
        int height = original.getHeight();

        if (rotation == 0 && width > height)
        {
            Log.d("ScanLibraryUtils", "pick getBitmap rotate 90");
            matrix.preRotate(90);
        }

        Bitmap newBitmap = Bitmap.createBitmap(original, 0, 0, width, height, matrix, true);
        Log.d("ScanLibraryUtils", "pick getBitmap end D: " + newBitmap.getByteCount());
        return newBitmap;
    }

    private static int exifToDegrees(int exifOrientation) {
        Log.d("ScanLibraryUtils", "exifToDegrees " + exifOrientation);
        switch (exifOrientation)
        {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
        }
        return 0;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(getActivity(), "camera permission denied", Toast.LENGTH_LONG).show();
            }

        }
    }

    protected void showProgressDialog(String message) {
        progressDialogFragment = new ProgressDialogFragment(message);
        FragmentManager fm = getFragmentManager();
        progressDialogFragment.show(fm, ProgressDialogFragment.class.toString());
    }
}