package com.modima.forwarder.socks;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

public class SocksResponseThread extends Thread {

    static final String TAG = SocksResponseThread.class.getName();

    private final InputStream in;
    private final OutputStream out;

    public SocksResponseThread(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        Log.e(TAG, "start response thread");
        try {
            byte[] reply = new byte[4096];
            int bytesRead;
            while (-1 != (bytesRead = in.read(reply))) {
                out.write(reply, 0, bytesRead);
                Log.d(TAG, bytesRead + " response bytes");
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}