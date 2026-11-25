// https://www.geeksforgeeks.org/how-to-make-a-floating-window-application-in-android/

package org.cagnulein.qzcompanionpeloton;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.util.Log;
import android.content.SharedPreferences;

public class FloatingWindowGFG extends Service {

	 private ViewGroup floatView;
	 private int LAYOUT_TYPE;
	 private WindowManager.LayoutParams floatWindowLayoutParam;
	 private WindowManager windowManager;
	 private Button maximizeBtn;

         SharedPreferences sharedPreferences;
         private boolean horizontalMode;
         private boolean fallbackAttempted;
         private WebView horizontalWebView;
         private final Handler mainHandler = new Handler(Looper.getMainLooper());
         private boolean expanded;
         private int collapsedWindowHeight;
         private int collapsedWindowY;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
        (prefs, key) -> {
            if (CompanionTextScale.AUTO_PREFERENCE.equals(key) ||
                    CompanionTextScale.PERCENT_PREFERENCE.equals(key)) {
                updateHorizontalTextScale();
                return;
            }
            if (CompanionMetrics.PREFERENCE.equals(key)) {
                if (horizontalMode && horizontalWebView != null) {
                    String selected = CompanionMetrics.normalize(
                            prefs.getString(CompanionMetrics.PREFERENCE,
                                    CompanionMetrics.DEFAULT_SELECTION));
                    mainHandler.post(() -> {
                        if (horizontalMode && horizontalWebView != null) {
                            String argument = JSONObject.quote(selected);
                            horizontalWebView.evaluateJavascript(
                                    "if (window.setCompanionMetricSelection) " +
                                    "window.setCompanionMetricSelection(" + argument + ");", null);
                        }
                    });
                }
                return;
            }
            if (floatWindowLayoutParam == null || windowManager == null || floatView == null) return;
            android.graphics.Point screen = new android.graphics.Point();
            windowManager.getDefaultDisplay().getSize(screen);
            String widthKey = FloatingWindowStyle.key(PREF_NAME_WIDTH, horizontalMode);
            String heightKey = FloatingWindowStyle.key(PREF_NAME_HEIGHT, horizontalMode);
            String xKey = FloatingWindowStyle.key(PREF_NAME_X, horizontalMode);
            String yKey = FloatingWindowStyle.key(PREF_NAME_Y, horizontalMode);
            boolean changed = false;
            if (widthKey.equals(key)) {
                floatWindowLayoutParam.width = Math.max(1, Math.min(prefs.getInt(widthKey, floatWindowLayoutParam.width), screen.x));
                changed = true;
            } else if (heightKey.equals(key)) {
                floatWindowLayoutParam.height = Math.max(1, Math.min(prefs.getInt(heightKey, floatWindowLayoutParam.height), screen.y));
                changed = true;
            } else if (xKey.equals(key)) {
                floatWindowLayoutParam.x = prefs.getInt(xKey, 0);
                changed = true;
            } else if (yKey.equals(key)) {
                floatWindowLayoutParam.y = prefs.getInt(yKey, 0);
                changed = true;
            }
            if (changed) {
                floatWindowLayoutParam.x = Math.max(0, Math.min(floatWindowLayoutParam.x,
                        screen.x - floatWindowLayoutParam.width));
                floatWindowLayoutParam.y = Math.max(0, Math.min(floatWindowLayoutParam.y,
                        screen.y - floatWindowLayoutParam.height));
                if (!expanded) {
                    collapsedWindowHeight = floatWindowLayoutParam.height;
                    collapsedWindowY = floatWindowLayoutParam.y;
                }
                windowManager.updateViewLayout(floatView, floatWindowLayoutParam);
                if (widthKey.equals(key) || heightKey.equals(key)) {
                    updateHorizontalTextScale();
                }
            }
        };

     // Preference key name
	 public static final String PREF_NAME_X = "floatWindowLayoutUpdateParamX";
	 public static final String PREF_NAME_Y = "floatWindowLayoutUpdateParamY";
	 public static final String PREF_NAME_WIDTH = "floatWindowLayoutUpdateParamWidth";
	 public static final String PREF_NAME_HEIGHT = "floatWindowLayoutUpdateParamHeight";

	 // As FloatingWindowGFG inherits Service class,
	 // it actually overrides the onBind method
	 @Override
	 public IBinder onBind(Intent intent) {
		  return null;
		}

	 @Override
	 public void onCreate() {
		  super.onCreate();

		  // The screen height and width are calculated, cause
		  // the height and width of the floating window is set depending on this
                  /*DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
		  int width = metrics.widthPixels;
                  int height = metrics.heightPixels;*/

		  // To obtain a WindowManager of a different Display,
		  // we need a Context for that display, so WINDOW_SERVICE is used
		  windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

		  // A LayoutInflater instance is created to retrieve the
		  // LayoutInflater for the floating_layout xml
		  LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);

		  // inflate a new view hierarchy from the floating_layout xml
		  floatView = (ViewGroup) inflater.inflate(R.layout.floating_layout, null);

		 sharedPreferences = getSharedPreferences("QZ", MODE_PRIVATE);
         horizontalMode = sharedPreferences.getBoolean(FloatingWindowStyle.PREFERENCE, false);
		 sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener);

		 int zoom = sharedPreferences.getInt("zoom", 100);

                  WebView wv = (WebView)floatView.findViewById(R.id.webview);
                  horizontalWebView = wv;
                  final String originalUrl = FloatingHandler._address;
                  final String displayUrl = FloatingWindowStyle.url(originalUrl, horizontalMode);
                  if (horizontalMode) {
                      setWebViewMargins(wv, 0);
                      // Expose only overlay-window actions, never general Android or QZ APIs.
                      wv.addJavascriptInterface(new CompanionJavascriptBridge(), "Android");
                  }
                  wv.setWebViewClient(new WebViewClient() {
                      @Override
                      public void onPageFinished(WebView view, String url) {
                          super.onPageFinished(view, url);
                          // Never inject into a redirected or unrelated page.
                          if (horizontalMode && displayUrl.equals(url)) {
                              injectHorizontalPatch(view);
                          }
                      }

                      @Override
                      public boolean shouldOverrideUrlLoading(WebView view, String url) {
                          view.loadUrl(url);
                          return true;
                      }

                      @Override
                      public void onReceivedHttpError(WebView view,
                              android.webkit.WebResourceRequest request,
                              android.webkit.WebResourceResponse response) {
                          // Old QZ builds may not serve hfloating.htm: retain the original view.
                          if (horizontalMode && !fallbackAttempted && request.isForMainFrame()
                                  && response.getStatusCode() == 404
                                  && displayUrl.equals(request.getUrl().toString())
                                  && !displayUrl.equals(originalUrl)) {
                              fallbackAttempted = true;
                              horizontalMode = false;
                              setWebViewMargins(wv, 20);
                              applySavedGeometry();
                              wv.getSettings().setUseWideViewPort(false);
                              wv.setInitialScale(zoom);
                              Log.w("QZ", "Horizontal template unavailable, using vertical view");
                              view.loadUrl(originalUrl);
                          }
                      }
                  });
                  WebSettings settings = wv.getSettings();
                  settings.setJavaScriptEnabled(true);
                  wv.setInitialScale(horizontalMode ? 100 : zoom);
                  wv.loadUrl(displayUrl);
                  wv.clearView();
                  wv.measure(100, 100);
                  wv.setAlpha(Float.valueOf(FloatingHandler._alpha) / 100.0f);
                  settings.setBuiltInZoomControls(true);
                  settings.setDisplayZoomControls(false);
                  settings.setUseWideViewPort(horizontalMode);
                  settings.setDomStorageEnabled(true);
                  Log.d("QZ","loadurl");


		  // WindowManager.LayoutParams takes a lot of parameters to set the
		  // the parameters of the layout. One of them is Layout_type.
		  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			   // If API Level is more than 26, we need TYPE_APPLICATION_OVERLAY
				LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
				} else {
				// If API Level is lesser than 26, then we can
				// use TYPE_SYSTEM_ERROR,
				// TYPE_SYSTEM_OVERLAY, TYPE_PHONE, TYPE_PRIORITY_PHONE.
				// But these are all
				// deprecated in API 26 and later. Here TYPE_TOAST works best.
				LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_TOAST;
				}

			// Now the Parameter of the floating-window layout is set.
		  // 1) The Width of the window will be 55% of the phone width.
		  // 2) The Height of the window will be 58% of the phone height.
		  // 3) Layout_Type is already set.
		  // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
		  // problem with this flag is key inputs can't be given to the EditText.
		  // This problem is solved later.
		  // 5) Next parameter is Layout_Format. System chooses a format that supports
		  // translucency by PixelFormat.TRANSLUCENT

         floatWindowLayoutParam = new WindowManager.LayoutParams(
                 1, 1, LAYOUT_TYPE,
                 WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                 PixelFormat.TRANSLUCENT);
         floatWindowLayoutParam.gravity = Gravity.TOP | Gravity.LEFT;
         applySavedGeometry();
         collapsedWindowHeight = floatWindowLayoutParam.height;
         collapsedWindowY = floatWindowLayoutParam.y;

		  // The ViewGroup that inflates the floating_layout.xml is
		  // added to the WindowManager with all the parameters
		  windowManager.addView(floatView, floatWindowLayoutParam);


		  // Another feature of the floating window is, the window is movable.
		  // The window can be moved at any position on the screen.
		  floatView.setOnTouchListener(new View.OnTouchListener() {
			   final WindowManager.LayoutParams floatWindowLayoutUpdateParam = floatWindowLayoutParam;
				double x;
				double y;
				double px;
				double py;

				@Override
				public boolean onTouch(View v, MotionEvent event) {

                                         Log.d("QZ","onTouch");

					 switch (event.getAction()) {
						  // When the window will be touched,
						  // the x and y position of that position
						  // will be retrieved
						  case MotionEvent.ACTION_DOWN:
						      x = floatWindowLayoutUpdateParam.x;
								y = floatWindowLayoutUpdateParam.y;

								// returns the original raw X
								// coordinate of this event
								px = event.getRawX();

								// returns the original raw Y
								// coordinate of this event
								py = event.getRawY();
								break;
								// When the window will be dragged around,
						  // it will update the x, y of the Window Layout Parameter
						  case MotionEvent.ACTION_MOVE:
						      floatWindowLayoutUpdateParam.x = (int) ((x + event.getRawX()) - px);
								floatWindowLayoutUpdateParam.y = (int) ((y + event.getRawY()) - py);

                                                                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                                                                myEdit.putInt(FloatingWindowStyle.key(PREF_NAME_X, horizontalMode), floatWindowLayoutUpdateParam.x);
                                                                myEdit.putInt(FloatingWindowStyle.key(PREF_NAME_Y, horizontalMode), floatWindowLayoutUpdateParam.y);
                                                                myEdit.apply();

								// updated parameter is applied to the WindowManager
								windowManager.updateViewLayout(floatView, floatWindowLayoutUpdateParam);
								break;
								}
							return false;
					}
			});
	 }

    private void setWebViewMargins(WebView view, int marginDp) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int px = (int) (marginDp * getResources().getDisplayMetrics().density + 0.5f);
        params.setMargins(px, px, px, px);
        view.setLayoutParams(params);
    }

    private void applySavedGeometry() {
        android.graphics.Point screen = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(screen);
        int barHeight = Math.min(screen.y, Math.max(1,
                (int) (FloatingWindowStyle.DEFAULT_BAR_HEIGHT_DP *
                        getResources().getDisplayMetrics().density + 0.5f)));
        int defaultWidth = horizontalMode ? screen.x : 800;
        int defaultHeight = horizontalMode ? barHeight : 400;
        String widthKey = FloatingWindowStyle.key(PREF_NAME_WIDTH, horizontalMode);
        String heightKey = FloatingWindowStyle.key(PREF_NAME_HEIGHT, horizontalMode);
        String xKey = FloatingWindowStyle.key(PREF_NAME_X, horizontalMode);
        String yKey = FloatingWindowStyle.key(PREF_NAME_Y, horizontalMode);
        int width = Math.max(1, Math.min(sharedPreferences.getInt(widthKey, defaultWidth), screen.x));
        int height = Math.max(1, Math.min(sharedPreferences.getInt(heightKey, defaultHeight), screen.y));
        int x = sharedPreferences.getInt(xKey, 0);
        int y = sharedPreferences.getInt(yKey, horizontalMode ? screen.y - height : 0);
        floatWindowLayoutParam.width = width;
        floatWindowLayoutParam.height = height;
        floatWindowLayoutParam.x = Math.max(0, Math.min(x, screen.x - width));
        floatWindowLayoutParam.y = Math.max(0, Math.min(y, screen.y - height));
        expanded = false;
        collapsedWindowHeight = floatWindowLayoutParam.height;
        collapsedWindowY = floatWindowLayoutParam.y;
        if (floatView != null && floatView.isAttachedToWindow()) {
            windowManager.updateViewLayout(floatView, floatWindowLayoutParam);
        }
    }

    private void injectHorizontalPatch(WebView view) {
        try (InputStream input = getAssets().open("companion_horizontal.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            // Initialize scaling only after the injection has installed its JS handler.
            view.evaluateJavascript(output.toString("UTF-8"),
                    ignored -> updateHorizontalTextScale());
        } catch (IOException error) {
            Log.e("QZ", "Failed to inject Companion horizontal overlay patch", error);
        }
    }

    private void updateHorizontalTextScale() {
        mainHandler.post(() -> {
            if (!horizontalMode || horizontalWebView == null ||
                    floatWindowLayoutParam == null || sharedPreferences == null) return;
            android.graphics.Point screen = new android.graphics.Point();
            windowManager.getDefaultDisplay().getSize(screen);
            int referenceHeight = Math.max(1,
                    (int) (FloatingWindowStyle.DEFAULT_BAR_HEIGHT_DP *
                            getResources().getDisplayMetrics().density + 0.5f));
            float scale = CompanionTextScale.calculate(
                    floatWindowLayoutParam.width, floatWindowLayoutParam.height,
                    screen.x, referenceHeight,
                    sharedPreferences.getBoolean(CompanionTextScale.AUTO_PREFERENCE, true),
                    sharedPreferences.getInt(CompanionTextScale.PERCENT_PREFERENCE,
                            CompanionTextScale.DEFAULT_PERCENT));
            horizontalWebView.evaluateJavascript(
                    "if(window.setCompanionTextScale)window.setCompanionTextScale(" +
                    Float.toString(scale) + ");", null);
        });
    }

    private void expandHorizontalWindow(int extraCssPixels) {
        if (!horizontalMode || floatWindowLayoutParam == null || floatView == null) return;
        android.graphics.Point screen = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(screen);
        int extraPixels = Math.max(0, (int) (extraCssPixels *
                getResources().getDisplayMetrics().density + 0.5f));
        int newHeight = Math.min(screen.y, collapsedWindowHeight + extraPixels);
        int originalBottom = collapsedWindowY + collapsedWindowHeight;
        floatWindowLayoutParam.height = newHeight;
        floatWindowLayoutParam.y = Math.max(0, Math.min(originalBottom - newHeight, screen.y - newHeight));
        expanded = newHeight > collapsedWindowHeight;
        windowManager.updateViewLayout(floatView, floatWindowLayoutParam);
    }

    private void restoreHorizontalWindow() {
        if (!horizontalMode || !expanded || floatWindowLayoutParam == null || floatView == null) return;
        floatWindowLayoutParam.height = collapsedWindowHeight;
        floatWindowLayoutParam.y = collapsedWindowY;
        expanded = false;
        windowManager.updateViewLayout(floatView, floatWindowLayoutParam);
    }

    public class CompanionJavascriptBridge {
        @JavascriptInterface
        public String getCompanionMetrics() {
            // Missing preference means use the previous WebView-local selection if available.
            return sharedPreferences.getString(CompanionMetrics.PREFERENCE, "");
        }

        @JavascriptInterface
        public void saveCompanionMetrics(String selectedCsv) {
            if (!horizontalMode || selectedCsv == null || selectedCsv.trim().isEmpty()) return;
            sharedPreferences.edit().putString(CompanionMetrics.PREFERENCE,
                    CompanionMetrics.normalize(selectedCsv)).apply();
        }

        @JavascriptInterface
        public void expandFloatingWindow(int extraCssPixels) {
            mainHandler.post(() -> expandHorizontalWindow(extraCssPixels));
        }

        @JavascriptInterface
        public void restoreFloatingWindow() {
            mainHandler.post(() -> restoreHorizontalWindow());
        }

        @JavascriptInterface
        public void enableDraggingMargins() {
            // The user positions Companion using its own geometry fields.
            // QZ's drag-margin events must not re-layout the Shield overlay.
        }

        @JavascriptInterface
        public void closeCompanionWindow() {
            mainHandler.post(() -> {
                if (!horizontalMode) return;
                // Suppress automatic UDP reopen until a manual show or a new session.
                MainActivity.horizontalOverlayDismissed = true;
                MainActivity.floating_open = false;
                stopSelf();
            });
        }
    }

    // It is called when stopService()
	 // method is called in MainActivity
	 @Override
	 public void onDestroy() {
		  super.onDestroy();
		  sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener);
		  stopSelf();
		  windowManager.removeView(floatView);
		}
}
