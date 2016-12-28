package com.example.android.sunshine;

import android.util.Log;

import com.example.android.sunshine.common.utilities.LogHelper;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Jan-2 on 27.12.2016.
 */

public class UpdateWeatherDataService extends WearableListenerService {

    private final static String TAG = LogHelper.LOG_TAG(UpdateWeatherDataService.class);

    @Override
    public void onCreate() {
        Log.d(TAG, "Created UpdateWeatherDataService");
        super.onCreate();

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "Received changed data.");
        super.onDataChanged(dataEventBuffer);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Message received");
        super.onMessageReceived(messageEvent);
    }

    @Override
    public void onPeerConnected(Node node) {
        Log.d(TAG, "Node connected");
        super.onPeerConnected(node);
    }

    @Override
    public void onPeerDisconnected(Node node) {
        Log.d(TAG, "Node disconnected");
        super.onPeerDisconnected(node);
    }
}
