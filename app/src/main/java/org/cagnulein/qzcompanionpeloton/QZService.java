package org.cagnulein.qzcompanionpeloton;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

public class QZService extends Service {
    private static final String LOG_TAG = "QZ:Service";
    int startMode;       // indicates how to behave if the service is killed
    IBinder binder;      // interface for clients that bind
    boolean allowRebind; // indicates whether onRebind should be used    
    int clientPort = 8002;
    Handler handler = new Handler();
    Runnable runnable = null;
    DatagramSocket socket = null;

    byte[] lmessage = new byte[1024];
    DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

    AtomicLong filePointer = new AtomicLong();
    String fileName = "";
    RandomAccessFile bufferedReader = null;
    boolean firstTime = false;
    static float lastSpeedFloat = 0;
    static float lastInclinationFloat = 0;
    static float lastResistanceFloat = 0;
    static String lastSpeed = "";
    static String lastInclination = "";
    String lastWattage = "";
    String lastCadence = "";
    static String lastResistance = "";
    String lastGear = "";

    int counterTruncate = 0;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    @Override
    public void onCreate() {
  // The service is being created
        //Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();
        Log.i(LOG_TAG, "Service onCreate");

        try {
            runnable = new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "Service run"); parse();
                }
            };
        } finally {
            if(socket != null){
                socket.close();
                Log.e(LOG_TAG, "socket.close()");
            }
        }

        if(runnable != null) {
            Log.i(LOG_TAG, "Service postDelayed");
            handler.postDelayed(runnable, 500);
        }
    }

    private void parse() {
        Log.d(LOG_TAG,"Parsing ");

        {
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);

				sendBroadcast(lastWattage + ";" + lastCadence + ";" + lastResistance + ";" + lastSpeed);
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            socket.close();
        }

        handler.postDelayed(runnable, 500);
    }

    public void sendBroadcast(String messageStr) {
        StrictMode.ThreadPolicy policy = new   StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {

            byte[] sendData = messageStr.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, getBroadcastAddress(), this.clientPort);
            socket.send(sendPacket);
            System.out.println(messageStr);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
    }
    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = null;
        try {
            dhcp = wifi.getDhcpInfo();
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++)
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "Service started");
      
        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        return allowRebind;
    }
    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }
    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
    }
}
