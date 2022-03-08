package com.modima.forwarder.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.modima.forwarder.MainActivity;
import com.modima.forwarder.R;
import com.modima.forwarder.net.Connection;

import java.io.Serializable;
import java.util.List;

public class ConnectionAdapter extends ArrayAdapter<Connection> implements Serializable {

    public ConnectionAdapter(Context context, List<Connection> connections) {
        super(context, R.layout.item_connection, connections);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_connection, parent, false);
        }

        // Lookup view for data population
        TextView tvCInfo = (TextView) convertView.findViewById(R.id.connectionInfo);
        TextView tvDInfo = (TextView) convertView.findViewById(R.id.destinationInfo);
        TextView tvStats = (TextView) convertView.findViewById(R.id.connectionStats);
        ImageButton btnDeleteCon = (ImageButton) convertView.findViewById(R.id.deleteConnection);

        // Get the data item for this position
        try {
            Connection connection = getItem(position);

            if (connection.type == Connection.Type.STATIC) {
                btnDeleteCon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity)getContext()).deleteConnection(position);
                        notifyDataSetChanged();
                    }
                });
            } else {
                // hide delete button on non static connections
                btnDeleteCon.setVisibility(View.INVISIBLE);
            }

            String cInfo = connection.type + "/" + connection.proto.toString() + ": " + connection.listenPort;
            tvCInfo.setText(cInfo);

            String dInfo = " -> " + connection.dstAddress.toString();
            tvDInfo.setText(dInfo);

            String cStats = "tx: ";
            if (connection.bytesSent > 1000000) {
                cStats += (connection.bytesSent / 1000000) + " MB";
            } else if (connection.bytesSent > 1000) {
                cStats += (connection.bytesSent/1000) + " kB";
            } else {
                cStats += connection.bytesSent + " bytes";
            }

            cStats += " | rx: ";
            if (connection.bytesReceived > 1000000) {
                cStats += (connection.bytesReceived / 1000000) + " MB";
            } else if (connection.bytesReceived > 1000) {
                cStats += (connection.bytesReceived/1000) + " kB";
            } else {
                cStats += connection.bytesReceived + " bytes";
            }

            tvStats.setText(cStats);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return the completed view to render on screen
        return convertView;
    }
}