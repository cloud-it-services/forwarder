package com.modima.forwarder;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

public class WifiListener extends Thread {

    static final String TAG = WifiListener.class.getName();
    private Handler handler;

    public WifiListener(Handler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public void run() {

        byte[] buf = new byte[4096];
        while (true) {
            try {
                if (MainActivity.wifiNet == null || MainActivity.wifiSocket == null || MainActivity.cellNet == null || MainActivity.cellSocket == null) {
                    Thread.sleep(1000);
                    continue;
                }
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                Log.d(TAG, "wait for packet from cellular...", null);
                MainActivity.cellSocket.receive(packet);
                Log.d(TAG, "...cellular packet received", null);
                if (MainActivity.dstIP != null && MainActivity.dstPort != 0) {
                    Log.d(TAG, "forward cellular packet...", null);
                    MainActivity.wifiSocket.send(new DatagramPacket(packet.getData(), packet.getLength(),MainActivity.dstIP, MainActivity.dstPort));
                    Log.d(TAG, "... cellular forwarded to " + MainActivity.dstIP.getHostName() + ":" + MainActivity.dstPort, null);
                    Message msg = handler.obtainMessage(MainActivity.MSG_ERROR,0,0,"");
                    handler.sendMessage(msg);
                }
            } catch (SocketTimeoutException e) {
            } catch (Exception e) {
                Log.e(TAG,e.getMessage(),e);
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,e.getMessage()));
            }
        }
    }
}
