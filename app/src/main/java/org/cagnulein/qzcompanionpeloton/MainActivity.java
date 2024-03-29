package org.cagnulein.qzcompanionpeloton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.app.Activity;

import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import static android.content.ContentValues.TAG;

import com.cgutman.androidremotedebugger.AdbUtils;
import com.cgutman.androidremotedebugger.console.ConsoleBuffer;
import com.cgutman.androidremotedebugger.devconn.DeviceConnection;
import com.cgutman.androidremotedebugger.devconn.DeviceConnectionListener;
import com.cgutman.androidremotedebugger.service.ShellService;
import com.cgutman.adblib.AdbCrypto;

public class MainActivity extends AppCompatActivity  implements DeviceConnectionListener {
    private ShellService.ShellServiceBinder binder;
    private static DeviceConnection connection;
    private Intent service;
    private static final String LOG_TAG = "QZ:AdbRemote";
    private static String lastCommand = "";
    private static boolean ADBConnected = false;
    private static String appLogs = "";
    public static boolean floating_open = false;
    public static boolean b_autofloating;

	private final ShellRuntime shellRuntime = new ShellRuntime();

    private static WeakReference<Context> sContextReference;

    // on below line we are creating variables.
    RadioGroup radioGroup;
    SharedPreferences sharedPreferences;
    EditText width;
    EditText height;
    EditText top;
    EditText left;
    EditText zoom;
    static Button dumplog;

    public void onDestroy() {
        if(floating_open)
            FloatingHandler.hide();
        super.onDestroy();
    }

    private boolean checkPermissions(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            return false;
        }
    }

    @Override
    public void notifyConnectionEstablished(DeviceConnection devConn) {
        ADBConnected = true;
        Log.i(LOG_TAG, "notifyConnectionEstablished" + lastCommand);
    }

    @Override
    public void notifyConnectionFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamClosed(DeviceConnection devConn) {
        ADBConnected = false;
        Log.e(LOG_TAG, "notifyStreamClosed");
    }

    @Override
    public AdbCrypto loadAdbCrypto(DeviceConnection devConn) {
        return AdbUtils.readCryptoConfig(getFilesDir());
    }

    @Override
    public boolean canReceiveData() {
        return true;
    }

    @Override
    public void receivedData(DeviceConnection devConn, byte[] data, int offset, int length) {
        Log.i(LOG_TAG, data.toString());
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public void consoleUpdated(DeviceConnection devConn, ConsoleBuffer console) {

    }


    private DeviceConnection startConnection(String host, int port) {
        /* Create the connection object */
        DeviceConnection conn = binder.createConnection(host, port);

        /* Add this activity as a connection listener */
        binder.addListener(conn, this);

        /* Begin the async connection process */
        conn.startConnect();

        return conn;
    }

    private DeviceConnection connectOrLookupConnection(String host, int port) {
        DeviceConnection conn = binder.findConnection(host, port);
        if (conn == null) {
            /* No existing connection, so start the connection process */
            conn = startConnection(host, port);
        }
        else {
            /* Add ourselves as a new listener of this connection */
            binder.addListener(conn, this);
        }
        return conn;
    }

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            binder = (ShellService.ShellServiceBinder)arg1;
            if (connection != null) {
                binder.removeListener(connection, MainActivity.this);
            }
            connection = connectOrLookupConnection("127.0.0.1", 5555);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            binder = null;
        }
    };

    public static void writeLog(String command) {
        Date date = new Date();
        Timestamp timestamp2 = new Timestamp(date.getTime());
        appLogs = appLogs + "\n" + timestamp2 + " " + command;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sContextReference = new WeakReference<>(this);
        setContentView(R.layout.activity_main);
        checkPermissions();

        sharedPreferences = getSharedPreferences("QZ",MODE_PRIVATE);
        radioGroup = findViewById(R.id.radiogroupDevice);
        width = findViewById(R.id.width);
        height = findViewById(R.id.height);
        top = findViewById(R.id.top);
        left = findViewById(R.id.left);
        zoom = findViewById(R.id.zoom);

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                RadioButton radioButton = findViewById(i);
                /*if(i == R.id.x11i) {
                    UDPListenerService.setDevice(UDPListenerService._device.x11i);
				}*/
                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                myEdit.putInt("device", i);
                myEdit.commit();
            }
        });

        width.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try{
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(FloatingWindowGFG.PREF_NAME_WIDTH, Integer.parseInt(String.valueOf(width.getText())));
                    myEdit.commit();
                } catch (NumberFormatException ex) {

                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        height.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(FloatingWindowGFG.PREF_NAME_HEIGHT, Integer.parseInt(String.valueOf(height.getText())));
                    myEdit.commit();
                } catch (NumberFormatException ex) {

                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        top.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(FloatingWindowGFG.PREF_NAME_Y, Integer.parseInt(String.valueOf(top.getText())));
                    myEdit.commit();
                } catch (NumberFormatException ex) {

                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        left.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(FloatingWindowGFG.PREF_NAME_X, Integer.parseInt(String.valueOf(left.getText())));
                    myEdit.commit();
                } catch (NumberFormatException ex) {

                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        zoom.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt("zoom", Integer.parseInt(String.valueOf(zoom.getText())));
                    myEdit.commit();
                } catch (NumberFormatException ex) {

                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        int w = sharedPreferences.getInt(FloatingWindowGFG.PREF_NAME_WIDTH, 800);
        width.setText(Integer.toString(w));
        height.setText(Integer.toString(sharedPreferences.getInt(FloatingWindowGFG.PREF_NAME_HEIGHT, 400)));
        top.setText(Integer.toString(sharedPreferences.getInt(FloatingWindowGFG.PREF_NAME_Y, 400)));
        zoom.setText(Integer.toString(sharedPreferences.getInt("zoom", 100)));
        left.setText(Integer.toString(sharedPreferences.getInt(FloatingWindowGFG.PREF_NAME_X, 400)));

        int device = sharedPreferences.getInt("device", R.id.bike);
        RadioButton radioButton;
        radioButton = findViewById(device);
        if(radioButton != null)
            radioButton.setChecked(true);

        boolean b_autosync = sharedPreferences.getBoolean("autosync", false);
        Switch autosync = findViewById(R.id.autosync);
        autosync.setChecked(b_autosync);
        autosync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                myEdit.putBoolean("autosync", autosync.isChecked());
                myEdit.commit();
            }
        });

        b_autofloating = sharedPreferences.getBoolean("autofloating", true);
        Switch autofloating = findViewById(R.id.autofloating);
        autofloating.setChecked(b_autofloating);
        autofloating.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                myEdit.putBoolean("autofloating", autofloating.isChecked());
                myEdit.commit();
                b_autofloating = autofloating.isChecked();
            }
        });

        final Context me = this;
        dumplog = findViewById(R.id.dumplog);
        dumplog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!floating_open)
                    FloatingHandler.show(me, QZService.address,  60);
                else
                    FloatingHandler.hide();

                floating_open = !floating_open;
            }
        });

        Button btnminuszoom = findViewById(R.id.btnminuszoom);
        btnminuszoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Integer.parseInt(String.valueOf(zoom.getText())) > 10) {
                    zoom.setText(String.valueOf(Integer.parseInt(String.valueOf(zoom.getText()) )- 10));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnpluszoom = findViewById(R.id.btnpluszoom);
        btnpluszoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                {
                    zoom.setText(String.valueOf(Integer.parseInt(String.valueOf(zoom.getText()) ) + 10));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnminustop = findViewById(R.id.btnminustop);
        btnminustop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Integer.parseInt(String.valueOf(top.getText())) > 50) {
                    top.setText(String.valueOf(Integer.parseInt(String.valueOf(top.getText()) )- 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnplustop = findViewById(R.id.btnplustop);
        btnplustop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                {
                    top.setText(String.valueOf(Integer.parseInt(String.valueOf(top.getText()) ) + 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnminusleft = findViewById(R.id.btnminusleft);
        btnminusleft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Integer.parseInt(String.valueOf(left.getText())) > 50) {
                    left.setText(String.valueOf(Integer.parseInt(String.valueOf(left.getText()) )- 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnplusleft = findViewById(R.id.btnplusleft);
        btnplusleft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                {
                    left.setText(String.valueOf(Integer.parseInt(String.valueOf(left.getText()) ) + 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnminuswidth = findViewById(R.id.btnminuswidth);
        btnminuswidth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Integer.parseInt(String.valueOf(width.getText())) > 50) {
                    width.setText(String.valueOf(Integer.parseInt(String.valueOf(width.getText()) )- 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnpluswidth = findViewById(R.id.btnpluswidth);
        btnpluswidth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                {
                    width.setText(String.valueOf(Integer.parseInt(String.valueOf(width.getText()) ) + 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnminusheight = findViewById(R.id.btnminusheight);
        btnminusheight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Integer.parseInt(String.valueOf(height.getText())) > 50) {
                    height.setText(String.valueOf(Integer.parseInt(String.valueOf(height.getText()) )- 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        Button btnplusheight = findViewById(R.id.btnpluheight);
        btnplusheight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                {
                    height.setText(String.valueOf(Integer.parseInt(String.valueOf(height.getText()) ) + 50));
                    if(floating_open) {
                        FloatingHandler.hide();
                        FloatingHandler.show(me, QZService.address, 60);
                    }

                }
            }
        });

        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), TcpServerService.class));
        } else {
            startService(new Intent(getApplicationContext(), TcpServerService.class));
        }*/

        AlarmReceiver alarm = new AlarmReceiver();
        alarm.setAlarm(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /* If we have old RSA keys, just use them */
            AdbCrypto crypto = AdbUtils.readCryptoConfig(getFilesDir());
            if (crypto == null) {
                /* We need to make a new pair */
                Log.i(LOG_TAG,
                        "This will only be done once.");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        AdbCrypto crypto;

                        crypto = AdbUtils.writeNewCryptoConfig(getFilesDir());

                        if (crypto == null) {
                            Log.e(LOG_TAG,
                                    "Unable to generate and save RSA key pair");
                            return;
                        }

                    }
                }).start();
            }

            if (binder == null) {
                service = new Intent(this, ShellService.class);

                /* Bind the service if we're not bound already. After binding, the callback will
                 * perform the initial connection. */
                getApplicationContext().bindService(service, serviceConn, Service.BIND_AUTO_CREATE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
            }
        }

        if(b_autosync) {
            MediaProjection media = new MediaProjection();
            media.startProjection(this);
        }

    }

    public static void showFloating() {
        Context me = sContextReference.get();
        Activity a = (Activity)me;
        a.runOnUiThread(new Runnable() {

            @Override
            public void run() {

                // Stuff that updates the UI
                dumplog.setVisibility(View.VISIBLE);
            }
        });

        floating_open = true;
        FloatingHandler.show(me, QZService.address, 60);
    }

    static public void sendCommand(String command) {
        if(ADBConnected) {
            StringBuilder commandBuffer = new StringBuilder();

            commandBuffer.append(command);

            /* Append a newline since it's not included in the command itself */
            commandBuffer.append('\n');

            /* Send it to the device */
            connection.queueCommand(commandBuffer.toString());
        } else {
            Log.e(LOG_TAG, "sendCommand ADB is not connected!");
        }
    }

    protected  void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(LOG_TAG, "onActivityResult " + requestCode + " " + resultCode);
        if (requestCode == MediaProjection.REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }
            startService(org.cagnulein.qzcompanionpeloton.ScreenCaptureService.getStartIntent(this, resultCode, data));
        }
    }
}