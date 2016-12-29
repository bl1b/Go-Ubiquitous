package com.example.android.sunshine;

import android.content.Intent;
import android.util.Log;

import com.example.android.sunshine.common.data.WeatherContract;
import com.example.android.sunshine.common.utilities.LogHelper;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * Created by Jan-2 on 27.12.2016.
 */

public class UpdateWeatherDataService extends WearableListenerService {

    private final static String TAG = LogHelper.LOG_TAG(UpdateWeatherDataService.class);

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "Received changed data.");

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEventBuffer);

        for (DataEvent event : events) {
            if (event.getDataItem().getUri().getEncodedPath().equals("/weather")) {
                Log.d(TAG, "Handling event: " + event.getDataItem().getUri());
                DataMap data = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                final long weatherDate = data.getLong(WeatherContract.WeatherEntry.COLUMN_DATE);
                final float weatherMinTemp = data.getFloat(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
                final float weatherMaxTemp = data.getFloat(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
                final int weatherId = data.getInt(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID);

                Intent broadcastIntent = new Intent("com.example.android.sunshine.WEATHER_UPDATE");
                broadcastIntent.putExtra(WeatherContract.WeatherEntry.COLUMN_DATE, weatherDate);
                broadcastIntent.putExtra(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, weatherMinTemp);
                broadcastIntent.putExtra(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, weatherMaxTemp);
                broadcastIntent.putExtra(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                sendBroadcast(broadcastIntent);

                continue;
            }

            Log.w(TAG, "Unhandled event URI: " + event.getDataItem().getUri());
        }
    }
}
