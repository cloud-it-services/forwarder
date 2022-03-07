package com.modima.forwarder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.modima.forwarder.databinding.ActivityMainBinding;
import com.modima.forwarder.net.Connection;
import com.modima.forwarder.net.SocksProxy;
import com.modima.forwarder.net.TCPConnection;
import com.modima.forwarder.net.UDPConnection;
import com.modima.forwarder.ui.ConfigFragment;
import com.modima.forwarder.ui.ConnectionsFragment;
import com.modima.forwarder.ui.SectionsPagerAdapter;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final int MSG_ERROR = 0;
    public static final int MSG_UPDATE_UI = 1;
    static final String TAG = MainActivity.class.getName();
    public static Network wifiNet;
    public static Network cellNet;
    public static InetAddress wifiAddress;
    public static InetAddress cellAddress;
    public static Handler handler;
    public static ArrayList<Connection> connections = new ArrayList<Connection>();
    public static SectionsPagerAdapter sectionsPagerAdapter;
    public static ConnectivityManager connectivityManager;
    public static SocksProxy proxy;

    final ConnectivityManager.NetworkCallback cbCellular = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.e(TAG, "cellular available");
            if (cellNet == null || network.getNetworkHandle() != cellNet.getNetworkHandle()) {
                try {
                    Log.d(TAG, "got cellular " + network.getNetworkHandle());
                    cellNet = network;
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
                }
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, ""));
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.e(TAG, "cellular lost");
            cellNet = null;
            handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
        }

        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            super.onBlockedStatusChanged(network, blocked);
            if (blocked) {
                Log.e(TAG, "cellular blocked");
                cellNet = null;
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, "cellular network was blocked"));
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            for (LinkAddress la : linkProperties.getLinkAddresses()) {
                InetAddress ip = la.getAddress();
                if (!ip.isLoopbackAddress() && ip instanceof Inet4Address) {
                    MainActivity.cellAddress = ip;
                    Log.e(TAG, "cellular IP: " + MainActivity.cellAddress.getHostAddress());
                    // TESTING
                    //addConnection("udp", 5522, new InetSocketAddress("8.8.8.8", 53));
                    // TESTING END
                }
            }
        }
    };

    final ConnectivityManager.NetworkCallback cbWifi = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.e(TAG, "wifi available");
            if (wifiNet == null || network.getNetworkHandle() != wifiNet.getNetworkHandle()) {
                try {
                    Log.d(TAG, "got wifi " + network.getNetworkHandle());
                    wifiNet = network;
                    connectivityManager.bindProcessToNetwork(wifiNet);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
                }
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, ""));
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.e(TAG, "wifi lost");
            wifiNet = null;
            handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
        }

        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            super.onBlockedStatusChanged(network, blocked);
            if (blocked) {
                Log.e(TAG, "wifi blocked");
                wifiNet = null;
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, "wifi network was blocked"));
                handler.sendMessage(handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
            }
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            for (LinkAddress la : linkProperties.getLinkAddresses()) {
                InetAddress ip = la.getAddress();
                if (!ip.isLoopbackAddress() && ip instanceof Inet4Address) {
                    MainActivity.wifiAddress = ip;
                    Log.e(TAG, "wifi IP: " + MainActivity.wifiAddress.getHostAddress());
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = binding.tabs;
        tabs.setupWithViewPager(viewPager);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        handler = new Handler(msg -> {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ConfigFragment configFragment = (ConfigFragment) sectionsPagerAdapter.getItem(0);
                    ConnectionsFragment connectionsFragment = (ConnectionsFragment) sectionsPagerAdapter.getItem(1);
                    switch (msg.what) {
                        case MSG_ERROR:
                            configFragment.setError((String) msg.obj);
                            break;
                        case MSG_UPDATE_UI:
                            configFragment.updateStatus();
                            if (connectionsFragment != null) {
                                connectionsFragment.updateUI();
                            }
                            break;
                    }
                }
            });
            return true;
        });

        initNetwork();
        if (proxy == null) {
            rebindProxy(null);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("proxy",proxy);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            proxy = (SocksProxy) savedInstanceState.getSerializable("proxy");
        }
    }

    public void initNetwork() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(), cbWifi);
            connectivityManager.requestNetwork(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build(), cbCellular);
        }
    }

    public void removeConnection(Connection con) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connections.remove(con);
                ConnectionsFragment connectionsFragment = (ConnectionsFragment) sectionsPagerAdapter.getItem(1);
                connectionsFragment.updateUI();
            }
        });
    }

    public void addConnection(Connection con) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connections.add(0, con);
                ConnectionsFragment connectionsFragment = (ConnectionsFragment) sectionsPagerAdapter.getItem(1);
                connectionsFragment.updateUI();
            }
        });
    }

    public void addConnection(String proto, int srcPort, InetSocketAddress dstAddress) {
        try {
            Connection conn;
            if (proto == "tcp") {
                conn = new TCPConnection(Connection.Type.STATIC, MainActivity.wifiNet, MainActivity.cellNet, dstAddress, MainActivity.this);
            } else {
                conn = new UDPConnection(Connection.Type.STATIC, MainActivity.wifiNet, MainActivity.cellNet, dstAddress, MainActivity.this);
            }
            //Log.e(TAG, "add connection");
            connections.add(0, conn);
            conn.listen(srcPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteConnection(int idx) {
        connections.remove(idx);
    }

    public void reconnectCellular(View view) {
        connectivityManager.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build(), cbWifi);
    }

    public void reconnectWifi(View view) {
        connectivityManager.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build(), cbCellular);
    }

    public void rebindProxy(View view) {
        try {
            if (proxy != null) {
                proxy.stop();
            }
            ConfigFragment cf = (ConfigFragment) sectionsPagerAdapter.getItem(0);
            int port = cf.getSocksPort();
            proxy = new SocksProxy(port, MainActivity.this);
            proxy.start();
            handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, ""));
        } catch (IOException e) {
            handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
            e.printStackTrace();
        }
    }
}