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


/**
 * Created by Jan-2 on 27.12.2016.
 */

public class WearableNotifier {
    private static final String TAG = LogHelper.LOG_TAG(WearableNotifier.class);

    private final GoogleApiClient apiClient;

    public static WearableNotifier createWithApiClient(GoogleApiClient apiClient) {
        return new WearableNotifier(apiClient);
    }

    private WearableNotifier(GoogleApiClient apiClient) {
        this.apiClient = apiClient;
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
}
