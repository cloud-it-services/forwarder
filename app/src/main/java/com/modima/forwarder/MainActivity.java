package com.modima.forwarder;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends Activity {

    public static final int MSG_ERROR = 0;
    public static final int MSG_UPDATE_UI = 1;
    private static final String TAG = MainActivity.class.getName();
    public static int dstPort;
    public static InetAddress dstIP;
    public static DatagramSocket wifiSocket;
    public static DatagramSocket cellSocket;
    public static Network wifiNet;
    public static Network cellNet;
    public Handler handler;
    private int srcPort;
    private int socketTimeout;
    private EditText editTextSrcPort;
    private EditText editTextSocketTimeout;
    private TextView textViewStatus;
    private TextView textViewErrors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextSrcPort = findViewById(R.id.srcPort);
        editTextSocketTimeout = findViewById(R.id.socketTimeout);
        textViewStatus = findViewById(R.id.status);
        textViewErrors = findViewById(R.id.errors);
        Button buttonBind = findViewById(R.id.bind);
        buttonBind.setOnClickListener(view -> {
            clearError();
            int newTimeout = Integer.parseInt(editTextSocketTimeout.getText().toString());
            int newPort = Integer.parseInt(editTextSrcPort.getText().toString());
            if (srcPort != newPort) {
                srcPort = newPort;
                try {
                    wifiSocket = bindSocket(wifiNet, socketTimeout, srcPort);
                    updateStatus();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    setError(e.getMessage());
                }
            }
            if (socketTimeout != newTimeout) {
                if (wifiSocket != null) {
                    new Thread(() -> {
                        try {
                            // this is blocking as long as somebody is using the socket
                            wifiSocket.setSoTimeout(socketTimeout);
                        } catch (SocketException e) {
                            Log.e(TAG, e.getMessage(), e);
                            setError(e.getMessage());
                        }

                    }).start();
                }
                if (cellSocket != null) {
                    new Thread(() -> {
                        try {
                            // this is blocking as long as somebody is using the socket
                            cellSocket.setSoTimeout(socketTimeout);
                        } catch (SocketException e) {
                            Log.e(TAG, e.getMessage(), e);
                            setError(e.getMessage());
                        }

                    }).start();
                }
            }
        });

        srcPort = Integer.parseInt(editTextSrcPort.getText().toString());
        socketTimeout = Integer.parseInt(editTextSocketTimeout.getText().toString());
        try {
            wifiSocket = new DatagramSocket(srcPort);
            wifiSocket.setSoTimeout(socketTimeout);
            cellSocket = new DatagramSocket();
            cellSocket.setSoTimeout(socketTimeout);
        } catch (SocketException e) {
            Log.e(TAG, e.getMessage(), e);
            setError(e.getMessage());
        }

        handler = new Handler(msg -> {
            switch (msg.what) {
                case MSG_ERROR:
                    setError((String) msg.obj);
                    break;
                case MSG_UPDATE_UI:
                    updateStatus();
                    break;
            }
            return true;
        });

        new WifiListener(handler).start();
        new CellularListener(handler).start();

        this.requestNetworks();
        this.updateStatus();
    }

    public void updateStatus() {
        String msg = "";
        msg += "wifi network ";
        msg += wifiNet != null ? "OK" + " (listen on port " + srcPort + ")" : "failed";
        msg += "\ncellular network ";
        msg += cellNet != null ? "OK" : "failed";
        textViewStatus.setText(msg);
    }

    protected DatagramSocket bindSocket(Network network, int timeout, int port) throws IOException {
        DatagramSocket s = new DatagramSocket(port);
        s.setSoTimeout(timeout);
        network.bindSocket(s);
        return s;

    }

    protected void clearError() {
        textViewErrors.setText("");
    }

    protected void setError(String msg) {
        textViewErrors.setText(msg);
    }

    protected void requestNetworks() {
        final ConnectivityManager.NetworkCallback cbWifi = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                if (wifiNet == null || network.getNetworkHandle() != wifiNet.getNetworkHandle()) {
                    try {
                        Log.d(TAG, "got wifi " + network.getNetworkHandle());
                        wifiNet = network;
                        if (wifiSocket != null) {
                            wifiNet.bindSocket(wifiSocket);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
                    }
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,""));
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                wifiNet = null;
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }

            @Override
            public void onBlockedStatusChanged(Network network, boolean blocked) {
                super.onBlockedStatusChanged(network, blocked);
                if (blocked) {
                    wifiNet = null;
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,"wifi network was blocked"));
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
            }
        };

        final ConnectivityManager.NetworkCallback cbCellular = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                if (cellNet == null || network.getNetworkHandle() != cellNet.getNetworkHandle()) {
                    try {
                        Log.d(TAG, "got cellular " + network.getNetworkHandle());
                        cellNet = network;
                        if (cellSocket != null) {
                            cellNet.bindSocket(cellSocket);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
                    }
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,""));
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                cellNet = null;
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }

            @Override
            public void onBlockedStatusChanged(Network network, boolean blocked) {
                super.onBlockedStatusChanged(network, blocked);
                if (blocked) {
                    cellNet = null;
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR,0,0,"cellular network was blocked"));
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
            }
        };

        final ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(), cbWifi);
        connectivityManager.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build(), cbCellular);
    }
}