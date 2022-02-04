package com.modima.forwarder;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class CellularListener extends Thread {

    static final String TAG = CellularListener.class.getName();
    private Handler handler;

    public CellularListener(Handler handler) {
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
                Log.d(TAG, "wait for packet from wifi...", null);
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                MainActivity.wifiSocket.receive(packet);
                MainActivity.dstIP = packet.getAddress();
                MainActivity.dstPort = packet.getPort();
                Log.d(TAG, "...wifi packet received from " + MainActivity.dstIP.getHostAddress() + ":" + MainActivity.dstPort, null);
                Log.d(TAG, "send wifi packet...", null);
                packet.setAddress(InetAddress.getByAddress(new byte[] {8, 8, 8, 8})); // debugging (remove in production)
                packet.setPort(53); // debugging (remove in production)
                MainActivity.cellSocket.send(new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort()));
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

