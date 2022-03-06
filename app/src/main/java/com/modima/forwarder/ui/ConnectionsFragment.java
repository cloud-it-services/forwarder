package com.modima.forwarder.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.modima.forwarder.MainActivity;
import com.modima.forwarder.R;

import java.net.InetSocketAddress;

public class ConnectionsFragment extends Fragment {

    public static ConnectionAdapter connectionAdapter;
    FloatingActionButton fab;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_connections, container, false);

        // Create the adapter to convert the array to views
        connectionAdapter = new ConnectionAdapter(getActivity(), MainActivity.connections);
        // Attach the adapter to a ListView
        ListView listView = (ListView) view.findViewById(R.id.connectionList);
        listView.setAdapter(connectionAdapter);

        fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNewConnectionDialog(getActivity());
            }
        });

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("connectionAdapter", connectionAdapter);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            // Restore last state for checked position.
            connectionAdapter = (ConnectionAdapter) savedInstanceState.getSerializable("connectionAdapter");
        }
    }

    public void updateUI() {
        //Log.e("CONNECTION FRAGMENT", "updateUI " + connectionAdapter);
        if (connectionAdapter != null) {
            connectionAdapter.notifyDataSetChanged();
        }
    }

    private void showNewConnectionDialog(Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_new_connection);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        Spinner sProto = (Spinner) dialog.findViewById(R.id.connectionProtocol);
        TextView tvSrcPort = (TextView) dialog.findViewById(R.id.connectionSrcPort);
        TextView tvDstIP = (TextView) dialog.findViewById(R.id.connectionDstIP);
        TextView tvDstPort = (TextView) dialog.findViewById(R.id.connectionDstPort);
        Button btnSaveConnection = (Button) dialog.findViewById(R.id.saveConnection);
        Button btnCancel = (Button) dialog.findViewById(R.id.cancelConnection);

        btnSaveConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String protocol = sProto.getSelectedItem().toString();
                int srcPort = Integer.parseInt(tvSrcPort.getText().toString());
                String dstIP = tvDstIP.getText().toString();
                int dstPort = Integer.parseInt(tvDstPort.getText().toString());
                InetSocketAddress dstAddress = new InetSocketAddress(dstIP, dstPort);
                ((MainActivity) getActivity()).addConnection(protocol, srcPort, dstAddress);
                Log.e("CONNECTION FRAGMENT", "showNewConnectionDialog " + connectionAdapter);
                connectionAdapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        /**
         * if you want the dialog to be specific size, do the following
         * this will cover 85% of the screen (85% width and 85% height)
         */
        /*
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int dialogWidth = (int)(displayMetrics.widthPixels * 0.85);
        int dialogHeight = (int)(displayMetrics.heightPixels * 0.85);
        dialog.getWindow().setLayout(dialogWidth, dialogHeight);
         */

        dialog.show();
    }
}
