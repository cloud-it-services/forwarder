package com.modima.forwarder.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.modima.forwarder.MainActivity;
import com.modima.forwarder.R;
import com.modima.forwarder.net.Connection;

import java.io.Serializable;
import java.util.ArrayList;

public class ConnectionAdapter extends ArrayAdapter<Connection> implements Serializable {

    public ConnectionAdapter(Context context, ArrayList<Connection> connections) {
        super(context, R.layout.item_connection, connections);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // Get the data item for this position
        Connection connection = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_connection, parent, false);
        }

        convertView.findViewById(R.id.deleteConnection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getContext()).deleteConnection(position);
                notifyDataSetChanged();
            }
        });

        // Lookup view for data population
        TextView tvInfo = (TextView) convertView.findViewById(R.id.connectionInfo);
        TextView tvStats = (TextView) convertView.findViewById(R.id.connectionStats);
        // Populate the data into the template view using the data object
        String cInfo = connection.type + "/" + connection.proto.toString() + ": " + connection.listenPort + " -> " + connection.dstAddress.toString();
        tvInfo.setText(cInfo);

        String cStats = "tx: " + (connection.bytesSent/1000) + " kB | rx: " + (connection.bytesReceived/1000) + " kB";
        tvStats.setText(cStats);
        // Return the completed view to render on screen
        return convertView;
    }
}