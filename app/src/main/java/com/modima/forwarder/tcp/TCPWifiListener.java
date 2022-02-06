package com.modima.forwarder.tcp;

import android.os.Handler;
import android.util.Log;

import com.modima.forwarder.MainActivity;
import com.modima.forwarder.socks.SocksServer;

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
                if (MainActivity.wifiSocketTCP == null || MainActivity.cellNet == null) {
                    Thread.sleep(1000);
                    continue;
                }

                //Log.d(TAG, "wait for packet from wifi...", null);
                Socket socket = MainActivity.wifiSocketTCP.accept();

                if (MainActivity.useProxy) {
                    Log.d(TAG, "...wifi packet received --> forward packet to socks proxy at " + MainActivity.proxyIP + ":" + MainActivity.proxyPort);
                    startThread(new Connection(socket, MainActivity.proxyIP, MainActivity.proxyPort));
                } else {
                    Log.d(TAG, "...wifi packet received --> handle local");
                    new SocksServer(socket).start();
                }

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

