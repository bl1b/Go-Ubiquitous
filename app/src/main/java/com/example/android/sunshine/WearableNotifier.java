package com.example.android.sunshine;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.example.android.sunshine.common.data.WeatherContract;
import com.example.android.sunshine.common.utilities.LogHelper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;

/**
 * Created by Jan-2 on 27.12.2016.
 */

public class WearableNotifier implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = LogHelper.LOG_TAG(WearableNotifier.class);

    private final FragmentActivity callingActivity;
    private GoogleApiClient apiClient;

    public static WearableNotifier createForCallingActivity(FragmentActivity callingActivity) {
        return new WearableNotifier(callingActivity);
    }

    private WearableNotifier(FragmentActivity callingActivity) {
        this.callingActivity = callingActivity;
        setupAPIClient();
    }

    private void setupAPIClient() {
        if (apiClient == null) {
            try {
                apiClient = new GoogleApiClient.Builder(this.callingActivity)
                        .enableAutoManage(callingActivity, new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                                Log.d(TAG, "OnConnectionFailed().");
                            }
                        })
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Wearable.API)
                        .build();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Trying to setup a new API-Client while there's still one running.", e);
            }
        }
    }

    public void disconnect() {
        if (apiClient != null) {
            apiClient.stopAutoManage(callingActivity);
            apiClient.disconnect();
            apiClient = null;
        }
    }

    public void notifyFromWeatherCursorData(Cursor data) {
        if (shouldNotify(data)) {
            int dateIndex = data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_DATE);
            int minTempIndex = data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
            int maxTempIndex = data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
            int weatherIdIndex = data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID);

            PutDataRequest dataRequest = createDataRequest(
                    data.getLong(dateIndex),
                    data.getFloat(minTempIndex),
                    data.getFloat(maxTempIndex),
                    data.getInt(weatherIdIndex)
            );

            Wearable.DataApi.putDataItem(apiClient, dataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    Log.d(TAG, "Wearable request result: " + dataItemResult.getStatus().getStatusMessage());
                    if (!dataItemResult.getStatus().isSuccess()) {
                        Log.w(TAG, "Failed to send wearable request data.");
                    }
                }
            });
        }
    }

    private static PutDataRequest createDataRequest(final long date, final float minTemp, final float maxTemp, final int weatherId) {
        PutDataMapRequest map = PutDataMapRequest.create("/weather");
        map.getDataMap().putLong(WeatherContract.WeatherEntry.COLUMN_DATE, date);
        map.getDataMap().putFloat(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, minTemp);
        map.getDataMap().putFloat(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, maxTemp);
        map.getDataMap().putInt(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);

        // for debugging purposes add a seed so that the onChanged() method will be called every time
        map.getDataMap().putLong("seed", System.currentTimeMillis());
        map = map.setUrgent();
        return map.asPutDataRequest();
    }

    private boolean shouldNotify(Cursor data) {
        return apiClient != null
                && data.moveToFirst();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connnected.");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "OnConnectionFailed()");
    }
}
