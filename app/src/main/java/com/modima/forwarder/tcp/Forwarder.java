package com.modima.forwarder.tcp;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

public class Forwarder implements Runnable {

    static final String TAG = Forwarder.class.getName();

    private final Socket in;
    private final Socket out;

    public Forwarder(Socket in, Socket out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        Log.i(TAG, "Proxy "+ in.getInetAddress().getHostName() +":"+ in.getPort()+" --> "+out.getInetAddress().getHostName()+":"+out.getPort(), null);
        try {
            InputStream inputStream = getInputStream();
            OutputStream outputStream = getOutputStream();

            if (inputStream == null || outputStream == null) {
                return;
            }

            byte[] reply = new byte[4096];
            int bytesRead;
            while (-1 != (bytesRead = inputStream.read(reply))) {
                outputStream.write(reply, 0, bytesRead);
            }
        } catch (SocketException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private InputStream getInputStream() {
        try {
            return in.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private OutputStream getOutputStream() {
        try {
            return out.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}