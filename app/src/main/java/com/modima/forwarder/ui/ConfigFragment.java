package com.modima.forwarder.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.modima.forwarder.MainActivity;
import com.modima.forwarder.R;

public class ConfigFragment extends Fragment {

    static final String TAG = ConfigFragment.class.getName();
    public static final int MSG_ERROR = 0;
    public static final int MSG_UPDATE_UI = 1;

    private TextView tvWifiStatus;
    private TextView tvCellNetStatus;
    private TextView tvSocksPort;
    private TextView tvErrors;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_config, container, false);

        tvWifiStatus = view.findViewById(R.id.wifiStatus);
        tvCellNetStatus = view.findViewById(R.id.cellNetStatus);
        tvSocksPort = view.findViewById(R.id.socksPort);
        tvErrors = view.findViewById(R.id.errors);

        this.updateStatus();
        return view;
    }

    public void clearError() {
        if (tvErrors != null) {
            tvErrors.setText("");
        }
    }

    public void setError(String msg) {
        if (tvErrors != null) {
            tvErrors.setText(msg);
        }
    }

    public int getSocksPort() {
        String strSocksPort = "";
        if (tvSocksPort != null) {
            strSocksPort = tvSocksPort.getText().toString();
        } else {
            strSocksPort = "4441";
        }
        return Integer.parseInt(strSocksPort);
    }

    public void updateStatus() {
        String msg = "";
        if (MainActivity.wifiNet != null) {
            msg += "wifi network OK\n";
            msg += MainActivity.wifiAddress.getHostAddress();
        } else {
            msg += "wifi network failed";
        }

        //Log.e("CONFIG FRAG", "updateStatus " + tvWifiStatus);
        if (tvWifiStatus != null) {
            tvWifiStatus.setText(msg);
        }

        msg = "";
        if (MainActivity.cellNet != null) {
            msg += "cellular network OK\n";
            msg += MainActivity.cellAddress.getHostAddress();
        } else {
            msg += "cellular network failed";
        }
        if (tvCellNetStatus != null) {
            tvCellNetStatus.setText(msg);
        }
    }
}
