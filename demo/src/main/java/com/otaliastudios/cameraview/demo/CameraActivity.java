package com.otaliastudios.cameraview.demo;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Toast;

import com.curi.tracker.lib.TrackerX;
import com.example.lib_curi_utility.CuriUtility;
import com.example.lib_gui.DragRectView;
import com.example.lib_gui.DrawRectView;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.otaliastudios.cameraview.SessionType;
import com.otaliastudios.cameraview.Size;

import java.io.File;


public class CameraActivity extends AppCompatActivity implements View.OnClickListener, ControlView.Callback {

    private CameraView camera;
    private ViewGroup controlPanel;


    private boolean mCapturingPicture;
    private boolean mCapturingVideo;

    // To show stuff in the callback
    private Size mCaptureNativeSize;
    private long mCaptureTime;
    Handler handler = null;
    String TAG = "isaac";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        setContentView(R.layout.activity_camera);
        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE);
        camera = findViewById(R.id.camera);
        handler = new Handler();
        camera.addFrameProcessor(new FrameProcessor() {

            TrackerX tracker = null;

            @Override
            public void process(@NonNull Frame frame) {

                if ((null == frame) || (null == frame.getData())) {
                    Log.d(TAG, "frame is null");
                    return;
                }
                byte[] data = frame.getData();
                int rotation = frame.getRotation();
                long time = frame.getTime();
                Size size = frame.getSize();
                int format = frame.getFormat();
                Log.d(TAG, String.format("%d, data.length=%d, size=(%d, %d), rotation=%d, format=%d",
                        time, data.length, size.getWidth(), size.getHeight(), rotation, format));

                int resizedWidth = 320;
                int resizedHeight = 240;
                double ratio_Width_preview_resized = (double) (camera.getWidth()) / resizedWidth;
                double ratio_Height_preview_resized = (double) (camera.getHeight()) / resizedHeight;
                byte[] data1 = new byte[resizedHeight * resizedWidth * 3 >> 1];
                long startTime = System.currentTimeMillis();   //获取开始时间
                CuriUtility.reduceYBytes(data, size.getWidth(), size.getHeight(), data1, resizedWidth, resizedHeight);

                long endTime = System.currentTimeMillis(); //获取结束时间
                System.out.println("降采样运行时间： " + (endTime - startTime) + "ms");

                final DragRectView dragRectView = findViewById(R.id.dragview);
                if (dragRectView.isDrawing() && dragRectView.getmRect() != null) {
                    Rect rect = dragRectView.getmRect();
                    rect.left /= ratio_Width_preview_resized;
                    rect.right /= ratio_Width_preview_resized;
                    rect.top /= ratio_Height_preview_resized;
                    rect.bottom /= ratio_Height_preview_resized;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dragRectView.setDrawing(false);
                        }
                    });

                    if (rect.right - rect.left > 10 && rect.bottom - rect.top > 10) {
                        tracker = new TrackerX();
                        tracker.Init(data1, resizedWidth, resizedHeight, 1, rect);
                        Log.d(TAG, "tracker is init.");
                        Log.d(TAG, String.format("dragRectView size: %d %d", dragRectView.getWidth(), dragRectView.getHeight()));
                        Log.d(TAG, String.format("camview size: %d %d", camera.getWidth(), camera.getHeight()));
                    }
                } else {
                    DrawRectView drawRectView = findViewById(R.id.drawView);
                    if (null != tracker) {
                        Log.d(TAG, "tracking...");
                        Rect rect = tracker.Track(data1);
                        if (null != rect) {
                            Log.d(TAG, String.format("rect0 %d %d %d %d.", rect.left, rect.top, rect.right, rect.bottom));
                            drawRectView.drawRects(new Rect[]{rect}, resizedWidth, resizedHeight, drawRectView.getWidth(), drawRectView.getHeight());
                        } else {
                            Log.d(TAG, "tracking return null.");
                            drawRectView.cleanScreen();
                        }
                    }
                }
            }
        });
        camera.setLifecycleOwner(this);
        camera.addCameraListener(new CameraListener() {
            public void onCameraOpened(CameraOptions options) {
                onOpened();
            }

            public void onPictureTaken(byte[] jpeg) {
                onPicture(jpeg);
            }

            @Override
            public void onVideoTaken(File video) {
                super.onVideoTaken(video);
                onVideo(video);
            }
        });

        findViewById(R.id.edit).setOnClickListener(this);
        findViewById(R.id.capturePhoto).setOnClickListener(this);
        findViewById(R.id.captureVideo).setOnClickListener(this);
        findViewById(R.id.toggleCamera).setOnClickListener(this);

        controlPanel = findViewById(R.id.controls);
        ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
        Control[] controls = Control.values();
        for (Control control : controls) {
            ControlView view = new ControlView(this, control, this);
            group.addView(view, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        controlPanel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
                b.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });
    }

    private void message(String content, boolean important) {
        int length = important ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
        Toast.makeText(this, content, length).show();
    }

    private void onOpened() {
        ViewGroup group = (ViewGroup) controlPanel.getChildAt(0);
        for (int i = 0; i < group.getChildCount(); i++) {
            ControlView view = (ControlView) group.getChildAt(i);
            view.onCameraOpened(camera);
        }

        Log.d(TAG, String.format("camview size: %d %d", camera.getWidth(), camera.getHeight()));
        for (int i : new int[]{R.id.dragview, R.id.drawView}
                ) {
            View v = findViewById(i);
            ViewGroup.LayoutParams p = v.getLayoutParams();
            p.width = camera.getWidth();
            p.height = camera.getHeight();
            v.setLayoutParams(p);
        }

    }

    private void onPicture(byte[] jpeg) {
        mCapturingPicture = false;
        long callbackTime = System.currentTimeMillis();
        if (mCapturingVideo) {
            message("Captured while taking video. Size=" + mCaptureNativeSize, false);
            return;
        }

        // This can happen if picture was taken with a gesture.
        if (mCaptureTime == 0) mCaptureTime = callbackTime - 300;
        if (mCaptureNativeSize == null) mCaptureNativeSize = camera.getPictureSize();

        PicturePreviewActivity.setImage(jpeg);
        Intent intent = new Intent(CameraActivity.this, PicturePreviewActivity.class);
        intent.putExtra("delay", callbackTime - mCaptureTime);
        intent.putExtra("nativeWidth", mCaptureNativeSize.getWidth());
        intent.putExtra("nativeHeight", mCaptureNativeSize.getHeight());
        startActivity(intent);

        mCaptureTime = 0;
        mCaptureNativeSize = null;
    }

    private void onVideo(File video) {
        mCapturingVideo = false;
        Intent intent = new Intent(CameraActivity.this, VideoPreviewActivity.class);
        intent.putExtra("video", Uri.fromFile(video));
        startActivity(intent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit:
                edit();
                break;
            case R.id.capturePhoto:
                capturePhoto();
                break;
            case R.id.captureVideo:
                captureVideo();
                break;
            case R.id.toggleCamera:
                toggleCamera();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        if (b.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            b.setState(BottomSheetBehavior.STATE_HIDDEN);
            return;
        }
        super.onBackPressed();
    }

    private void edit() {
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        b.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void capturePhoto() {
        if (mCapturingPicture) return;
        mCapturingPicture = true;
        mCaptureTime = System.currentTimeMillis();
        mCaptureNativeSize = camera.getPictureSize();
        message("Capturing picture...", false);
        camera.capturePicture();
    }

    private void captureVideo() {
        if (camera.getSessionType() != SessionType.VIDEO) {
            message("Can't record video while session type is 'picture'.", false);
            return;
        }
        if (mCapturingPicture || mCapturingVideo) return;
        mCapturingVideo = true;
        message("Recording for 8 seconds...", true);
        camera.startCapturingVideo(null, 8000);
    }

    private void toggleCamera() {
        if (mCapturingPicture) return;
        switch (camera.toggleFacing()) {
            case BACK:
                message("Switched to back camera!", false);
                break;

            case FRONT:
                message("Switched to front camera!", false);
                break;
        }
    }

    @Override
    public boolean onValueChanged(Control control, Object value, String name) {
        if (!camera.isHardwareAccelerated() && (control == Control.WIDTH || control == Control.HEIGHT)) {
            if ((Integer) value > 0) {
                message("This device does not support hardware acceleration. " +
                        "In this case you can not change width or height. " +
                        "The view will act as WRAP_CONTENT by default.", true);
                return false;
            }
        }
        control.applyValue(camera, value);
        BottomSheetBehavior b = BottomSheetBehavior.from(controlPanel);
        b.setState(BottomSheetBehavior.STATE_HIDDEN);
        message("Changed " + control.getName() + " to " + name, false);
        return true;
    }

    //region Permissions

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean valid = true;
        for (int grantResult : grantResults) {
            valid = valid && grantResult == PackageManager.PERMISSION_GRANTED;
        }
        if (valid && !camera.isStarted()) {
            camera.start();
        }
    }

    //endregion
}
