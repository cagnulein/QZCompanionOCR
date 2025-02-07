package org.cagnulein.qzcompanionpeloton;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.Duration;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.media.ImageReader.OnImageAvailableListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import android.graphics.Rect;
import android.graphics.Point;

import androidx.core.util.Pair;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String RESULT_CODE = "RESULT_CODE";
    private static final String DATA = "DATA";
    private static final String ACTION = "ACTION";
    private static final String START = "START";
    private static final String STOP = "STOP";
    private static final String SCREENCAP_NAME = "screencap";

    private static int IMAGES_PRODUCED;

    private MediaProjection mMediaProjection;
    private String mStoreDir;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;

    private TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private static String lastText = "";
    private static boolean isRunning = false;

    public static String getLastText() {
        Log.d(TAG, "Getting last text: " + lastText);
        return lastText;
    }

    public static Intent getStartIntent(Context context, int resultCode, Intent data) {
        Log.d(TAG, "Creating start intent for screen capture");
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, START);
        intent.putExtra(RESULT_CODE, resultCode);
        intent.putExtra(DATA, data);
        return intent;
    }

    public static Intent getStopIntent(Context context) {
        Log.d(TAG, "Creating stop intent for screen capture");
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, STOP);
        return intent;
    }

    private static boolean isStartCommand(Intent intent) {
        boolean isStart = intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                && intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), START);
        Log.d(TAG, "Checking if start command: " + isStart);
        return isStart;
    }

    private static boolean isStopCommand(Intent intent) {
        boolean isStop = intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), STOP);
        Log.d(TAG, "Checking if stop command: " + isStop);
        return isStop;
    }

    private static int getVirtualDisplayFlags() {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    }

    public static Duration parseDuration(String durStr) {
        Log.d(TAG, "Parsing duration string: " + durStr);
        String isoString = durStr.replaceFirst("^(\\d{1,2}):(\\d{2})$", "PT$1M$2S");
        Duration duration = Duration.parse(isoString);
        Log.d(TAG, "Parsed duration: " + duration);
        return duration;
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "New image available for processing");
            FileOutputStream fos = null;
            try (Image image = mImageReader.acquireLatestImage()) {
                if (image != null) {
                    if(!isRunning) {
                        Log.d(TAG, "Starting image processing");
                        Instant start = Instant.now();
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mWidth;

                        isRunning = true;

                        final Bitmap bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);

                        Log.d(TAG, "Created bitmap with dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);

                        Task<Text> result = recognizer.process(inputImage)
                                .addOnSuccessListener(new OnSuccessListener<Text>() {
                                    @Override
                                    public void onSuccess(Text result) {
                                        Instant end = Instant.now();
                                        Duration delta = Duration.between(start, end);
                                        String resultText = result.getText();
                                        lastText = resultText;

                                        Log.d(TAG, "OCR processing completed in " + delta.toMillis() + "ms");
                                        Log.v(TAG, "OCR result: " + resultText);

                                        resultText = resultText.toUpperCase(Locale.ROOT);
                                        QZService.lastFullString = resultText;
                                        QZService.lastScreenShotTimeStamp = start.toString();
                                        QZService.lastOCRTimeStamp = end.toString();
                                        QZService.lastDeltaTimeStamp = delta.toString();

                                        String[] list = resultText.split("\n");
                                        boolean waitCadence = false;
                                        boolean waitPower = false;
                                        boolean waitResistance = false;
                                        boolean waitSpeed = false;
                                        boolean timerFound = false;

                                        Log.d(TAG, "Processing " + list.length + " lines of text");

                                        for(String l: list) {
                                            Pattern p = Pattern.compile("\\d\\d:\\d\\d");
                                            Matcher m = p.matcher(l);
                                            if (m.matches() && !timerFound) {
                                                try {
                                                    Duration dtime = parseDuration(l);
                                                    Duration newtime = dtime.minus(delta);
                                                    String snewtime = String.format("%02d:%02d", (newtime.getSeconds() % 3600) / 60, (newtime.getSeconds() % 60));
                                                    QZService.lastCountdown = snewtime;
                                                    Log.d(TAG, "Found and processed timer: " + snewtime);
                                                } catch (Exception ex) {
                                                    Log.e(TAG, "Error processing timer: " + ex.getMessage());
                                                    QZService.lastCountdown = l;
                                                }
                                                timerFound = true;
                                            }

                                            if(l.startsWith("CADENCE")) {
                                                waitCadence = true;
                                                Log.d(TAG, "Found CADENCE header");
                                            } else if(l.startsWith("RESISTANCE")) {
                                                waitResistance = true;
                                                Log.d(TAG, "Found RESISTANCE header");
                                            } else if(l.startsWith("OUTPUT")) {
                                                waitPower = true;
                                                Log.d(TAG, "Found OUTPUT header");
                                            } else if(l.startsWith("SPEED")) {
                                                waitSpeed = true;
                                                Log.d(TAG, "Found SPEED header");
                                            } else if(waitCadence) {
                                                waitCadence = false;
                                                QZService.lastCadence = l;
                                                Log.d(TAG, "Updated cadence: " + l);
                                            } else if(waitPower) {
                                                waitPower = false;
                                                QZService.lastWattage = l;
                                                Log.d(TAG, "Updated power: " + l);
                                            } else if(waitResistance) {
                                                waitResistance = false;
                                                QZService.lastResistance = l;
                                                Log.d(TAG, "Updated resistance: " + l);
                                            } else if(waitSpeed) {
                                                waitSpeed = false;
                                                QZService.lastSpeed = l;
                                                Log.d(TAG, "Updated speed: " + l);
                                            }
                                        }

                                        bitmap.recycle();
                                        isRunning = false;
                                        Log.d(TAG, "Completed processing cycle");
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception e) {
                                        Log.e(TAG, "OCR processing failed: " + e.getMessage());
                                        isRunning = false;
                                    }
                                });
                    } else {
                        Log.d(TAG, "Skipping image - processing already in progress");
                    }
                } else {
                    Log.d(TAG, "Null image received");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
            Log.d(TAG, "OrientationChangeCallback initialized");
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                Log.d(TAG, "Orientation changed. Old: " + mRotation + ", New: " + rotation);
                mRotation = rotation;
                try {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);

                    createVirtualDisplay();
                    Log.d(TAG, "Virtual display recreated after orientation change");
                } catch (Exception e) {
                    Log.e(TAG, "Error handling orientation change: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.d(TAG, "Media projection stopping");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) {
                        Log.d(TAG, "Releasing virtual display");
                        mVirtualDisplay.release();
                    }
                    if (mImageReader != null) {
                        Log.d(TAG, "Removing image reader listener");
                        mImageReader.setOnImageAvailableListener(null, null);
                    }
                    if (mOrientationChangeCallback != null) {
                        Log.d(TAG, "Disabling orientation change callback");
                        mOrientationChangeCallback.disable();
                    }
                    mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                    Log.d(TAG, "Media projection cleanup completed");
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bind requested");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service creating");

        mHandler = new Handler(Looper.getMainLooper());

        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.getAbsolutePath() + "/screenshots/";
            File storeDirectory = new File(mStoreDir);
            if (!storeDirectory.exists()) {
                boolean success = storeDirectory.mkdirs();
                if (!success) {
                    Log.e(TAG, "Failed to create file storage directory");
                    stopSelf();
                } else {
                    Log.d(TAG, "Created storage directory: " + mStoreDir);
                }
            }
        } else {
            Log.e(TAG, "Failed to get external files directory");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service start command received");

        if (isStartCommand(intent)) {
            Pair<Integer, Notification> notification = NotificationUtils.getNotification(this);
            startForeground(notification.first, notification.second);
            Log.d(TAG, "Starting foreground service");

            int resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(DATA);
            startProjection(resultCode, data);
        } else if (isStopCommand(intent)) {
            Log.d(TAG, "Stopping projection and service");
            stopProjection();
            stopSelf();
        } else {
            Log.d(TAG, "Invalid command received, stopping service");
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        Log.d(TAG, "Starting media projection");
        MediaProjectionManager mpManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data);
            if (mMediaProjection != null) {
                Log.d(TAG, "Media projection created successfully");
                mMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);

                mDensity = Resources.getSystem().getDisplayMetrics().densityDpi;
                WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                mDisplay = windowManager.getDefaultDisplay();
                Log.d(TAG, "Display density: " + mDensity);

                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                    Log.d(TAG, "Orientation change callback enabled");
                }

                createVirtualDisplay();
            } else {
                Log.e(TAG, "Failed to create media projection");
            }
        }
    }

    private void stopProjection() {
        Log.d(TAG, "Stopping projection");
        if (mHandler != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mMediaProjection != null) {
                        mMediaProjection.stop();
                        Log.d(TAG, "Media projection stopped");
                    }
                }
            });
        }
    }

    @SuppressLint("WrongConstant")
    private void createVirtualDisplay() {
        Log.d(TAG, "Creating virtual display");
        mWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        mHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
        Log.d(TAG, "Screen dimensions: " + mWidth + "x" + mHeight);

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight,
                mDensity, getVirtualDisplayFlags(), mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
        Log.d(TAG, "Virtual display created successfully");
    }
}