package com.modima.forwarder.upd;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

public class UDPWifiListener extends Thread {

    static final String TAG = UDPWifiListener.class.getName();
    private Handler handler;

    public UDPWifiListener(Handler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public void run() {

        byte[] buf = new byte[4096];
        while (true) {
            try {
                if (MainActivity.wifiNet == null || MainActivity.wifiSocketUDP == null || MainActivity.cellNet == null || MainActivity.cellSocketUDP == null) {
                    Thread.sleep(1000);
                    continue;
                }
                Log.d(TAG, "wait for packet from wifi...", null);
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                MainActivity.wifiSocketUDP.receive(packet);
                MainActivity.dstIP = packet.getAddress();
                MainActivity.dstPort = packet.getPort();
                Log.d(TAG, "...wifi packet received from " + MainActivity.dstIP.getHostAddress() + ":" + MainActivity.dstPort, null);
                Log.d("UDP", packet.getData().toString());
                Log.d(TAG, "send wifi packet...", null);
                //packet.setAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8})); // debugging (remove in production)
                //packet.setPort(53); // debugging (remove in production)
                MainActivity.cellSocketUDP.send(new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort()));
                Log.d(TAG, "...send wifi ok", null);
                Message msg = handler.obtainMessage(MainActivity.MSG_ERROR,0,0,"");
                handler.sendMessage(msg);
            } catch (SocketTimeoutException e) {
            } catch (Exception e) {
                Log.e(TAG,e.getMessage(),e);
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,e.getMessage()));
            }
        }
    }
}

