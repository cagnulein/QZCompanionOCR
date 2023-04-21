package org.cagnulein.qzcompanionpeloton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.TextView;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class UDPListenerService extends Service {
    private static final String LOG_TAG = "QZ:UDPListenerService";

    static String UDP_BROADCAST = "UDPBroadcast";

    //Boolean shouldListenForUDPBroadcast = false;
    static DatagramSocket socket;

    static double lastReqSpeed;
    static int y1Speed;      //vertical position of slider at 2.0
    static double lastReqInclination = 0;
    static int y1Inclination;    //vertical position of slider at 0.0
    static double lastReqResistance = 0;
    static int y1Resistance;

    static long lastSwipeMs = 0;
    static double reqCachedSpeed = -1;
    static double reqCachedResistance = -1;
    static double reqCachedInclination = -100;

    public enum _device {
        bike,
        bikeplus,
        tread,
        rower,
    }

    public static _device device;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    private static Instant lastURLReceivedTS;

    public static void setDevice(_device dev) {
        switch(dev) {
            /*case x11i:
                lastReqSpeed = 0;
                y1Speed = 600;      //vertical position of slider at 2.0
                y1Inclination = 557;    //vertical position of slider at 0.0
                break;*/
            
            default:
                break;
        }
        device = dev;
    }

    private void writeLog(String command) {
        MainActivity.writeLog(command);
        Log.i(LOG_TAG, command);
    }

    private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire();

        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }
        //socket.setSoTimeout(1000);
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        writeLog("Waiting for UDP broadcast");
        socket.receive(packet);

        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        writeLog("Got UDP broadcast from " + senderIP + ", message: " + message);

        writeLog(message);
        if(message.contains("http")) {
            lastURLReceivedTS = Instant.now();
            Boolean refreshURL = false;
            if(!QZService.address.isEmpty() && !QZService.address.equals(message))
                refreshURL = true;
            QZService.address = message;
            if(!MainActivity.floating_open) {
                SharedPreferences sharedPreferences = getSharedPreferences("QZ",MODE_PRIVATE);
                boolean b_autofloating = sharedPreferences.getBoolean("autofloating", true);
                if(b_autofloating) {
                    MainActivity.showFloating();
                }
            } else if(refreshURL) {
                FloatingHandler.hide();
                FloatingHandler.show(QZService.address);
            }
        }

        if(!QZService.address.isEmpty() && Duration.between(lastURLReceivedTS, Instant.now()).abs().getSeconds() > 5) {
            QZService.address = "";
            MainActivity.floating_open = false;
            FloatingHandler.hide();
        }

        broadcastIntent(senderIP, message);
        //socket.close();
    }

    private double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    private void broadcastIntent(String senderIP, String message) {
        Intent intent = new Intent(UDPListenerService.UDP_BROADCAST);
        intent.putExtra("sender", senderIP);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    Thread UDPBroadcastThread;

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

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
				{					
					try {
						InetAddress broadcastIP = getBroadcastAddress();
						Integer port = 8003;
						while (shouldRestartSocketListen) {
							listenAndWaitAndThrowIntent(broadcastIP, port);
						}
						//if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
					} catch (Exception e) {
                        writeLog("no longer listening for UDP broadcasts cause of error " + e.getMessage());
					}
				}
            }
        });
        UDPBroadcastThread.start();
    }

    private Boolean shouldRestartSocketListen=true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopListen();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        writeLog("Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
