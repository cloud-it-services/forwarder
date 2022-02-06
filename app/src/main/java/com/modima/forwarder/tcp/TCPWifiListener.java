package com.modima.forwarder.tcp;

import android.os.Handler;
import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.net.Socket;
import java.net.SocketTimeoutException;

public class TCPWifiListener extends Thread {

    static final String TAG = TCPWifiListener.class.getName();
    private final Handler handler;

    public TCPWifiListener(Handler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public void run() {

        while (true) {
            try {
                if (MainActivity.wifiSocketTCP == null || MainActivity.cellNet == null || !MainActivity.useProxy) {
                    Thread.sleep(1000);
                    continue;
                }

                //Log.d(TAG, "wait for packet from wifi...", null);
                Socket socket = MainActivity.wifiSocketTCP.accept();
                //Log.d(TAG, "...wifi packet received --> forward packet");
                startThread(new Connection(socket, MainActivity.proxyIP, MainActivity.proxyPort));

            } catch (SocketTimeoutException e) {
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
            }
        }
    }

    private void startThread(Connection connection) {
        Thread t = new Thread(connection);
        t.start();
    }
}

