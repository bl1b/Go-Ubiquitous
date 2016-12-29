/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.common.data.WeatherContract;
import com.example.android.sunshine.common.utilities.LogHelper;
import com.example.android.sunshine.common.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final String TAG = LogHelper.LOG_TAG(SunshineWatchFace.class);

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * Handler message id for update the weather periodically in interactive mode.
     */
    private static final int MSG_UPDATE_WEATHER = 1;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                    case MSG_UPDATE_WEATHER:
                        Log.d(TAG, "Handling update weather message.");
                        engine.handleUpdateWeatherMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler updateWatchfaceHandler = new EngineHandler(this);

        Paint backgroundUpper;

        Paint backgroundLower;

        Paint timePaint;
        float timeYOffset;

        Paint datePaint;
        float dateYOffset;

        boolean mAmbient;
        Calendar mCalendar;

        boolean mRegisteredTimeZoneReceiver = false;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        Paint minTempPaint;
        String textMinTemp;

        Paint maxTempPaint;
        String textMaxTemp;

        float tempYOffset;

        Paint weatherPaint;
        Bitmap weatherBitmap;

        boolean registeredWeatherUpdateReceiver = false;
        final BroadcastReceiver weatherUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received weather update broadcast.");

                final float minTemperature = intent.getFloatExtra(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, 0.0f);
                final float maxTemperature = intent.getFloatExtra(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, 0.0f);
                final int weatherId = intent.getIntExtra(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, 0);

                textMinTemp = SunshineWeatherUtils.formatTemperature(getApplicationContext(), minTemperature);
                textMaxTemp = SunshineWeatherUtils.formatTemperature(getApplicationContext(), maxTemperature);
                weatherBitmap = BitmapFactory.decodeResource(getResources(), SunshineWeatherUtils.getSmallArtResourceIdForWeatherCondition(weatherId));

                invalidate();
            }
        };

        boolean isRound = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        GoogleApiClient googleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            googleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            timeYOffset = resources.getDimension(R.dimen.watchface_text_time_y_offset);
            dateYOffset = resources.getDimension(R.dimen.watchface_text_date_y_offset);
            tempYOffset = resources.getDimension(R.dimen.watchface_text_temperature_y_offset);

            backgroundUpper = new Paint();
            backgroundUpper.setColor(ContextCompat.getColor(getApplicationContext(), R.color.watchface_background_upper_color));

            backgroundLower = new Paint();
            backgroundLower.setColor(ContextCompat.getColor(getApplicationContext(), R.color.watchface_background_lower_color));

            timePaint = new Paint();
            timePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.watchface_text_time_color), Paint.Align.CENTER);
            timePaint.setTextSize(resources.getDimension(R.dimen.watchface_text_time_textsize));

            datePaint = new Paint();
            datePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.watchface_text_date_color), Paint.Align.CENTER);
            datePaint.setTextSize(resources.getDimension(R.dimen.watchface_text_date_textsize));

            minTempPaint = new Paint();
            minTempPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.watchface_text_mintemperature_color));
            minTempPaint.setTextSize(resources.getDimension(R.dimen.watchface_text_temp_textsize));

            maxTempPaint = new Paint();
            maxTempPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.watchface_text_maxtemperature_color));
            maxTempPaint.setTextSize(resources.getDimension(R.dimen.watchface_text_temp_textsize));

            weatherPaint = new Paint();

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            updateWatchfaceHandler.removeMessages(MSG_UPDATE_TIME);
            updateWatchfaceHandler.removeMessages(MSG_UPDATE_WEATHER);
            releaseGoogleApiClient();
            super.onDestroy();
        }

        private void releaseGoogleApiClient() {
            if (googleApiClient != null && (googleApiClient.isConnected() || googleApiClient.isConnecting())) {
                googleApiClient.disconnect();
            }
        }

        private void connectGoogleApiClient() {
            if (googleApiClient != null && !(googleApiClient.isConnected() || googleApiClient.isConnecting())) {
                googleApiClient.connect();
            }
        }

        private Paint createTextPaint(int textColor) {
            return createTextPaint(textColor, Paint.Align.LEFT);
        }

        private Paint createTextPaint(int textColor, Paint.Align textAlign) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextAlign(textAlign);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
                connectGoogleApiClient();
            } else {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
            }

            if (!registeredWeatherUpdateReceiver) {
                registeredWeatherUpdateReceiver = true;
                IntentFilter filter = new IntentFilter("com.example.android.sunshine.WEATHER_UPDATE");
                SunshineWatchFace.this.registerReceiver(weatherUpdateReceiver, filter);
            }

        }

        private void unregisterReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            }

            if (registeredWeatherUpdateReceiver) {
                registeredWeatherUpdateReceiver = false;
                SunshineWatchFace.this.unregisterReceiver(weatherUpdateReceiver);
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            isRound = insets.isRound();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            // do not use bold typefaces with burnIn-Protection enabled
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            if (timePaint != null) {
                timePaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : Typeface.DEFAULT_BOLD);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                if (mLowBitAmbient) {
                    timePaint.setAntiAlias(!inAmbientMode);
                    datePaint.setAntiAlias(!inAmbientMode);
                    minTempPaint.setAntiAlias(!inAmbientMode);
                    maxTempPaint.setAntiAlias(!inAmbientMode);
                }

                Resources resources = SunshineWatchFace.this.getResources();
                Context context = SunshineWatchFace.this.getApplicationContext();

                if (mAmbient) {
                    minTempPaint.setColor(ContextCompat.getColor(context, R.color.watchface_text_mintemperature_color_ambient));
                    maxTempPaint.setColor(ContextCompat.getColor(context, R.color.watchface_text_maxtemperature_color_ambient));
                } else {
                    minTempPaint.setColor(ContextCompat.getColor(context, R.color.watchface_text_mintemperature_color));
                    maxTempPaint.setColor(ContextCompat.getColor(context, R.color.watchface_text_maxtemperature_color));
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height() / 2, backgroundUpper);
                canvas.drawRect(0, bounds.height() / 2, bounds.width(), bounds.height(), backgroundLower);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String timeText = mAmbient
                    ? String.format(Locale.getDefault(), "%tR", mCalendar)
                    : String.format(Locale.getDefault(), "%tT", mCalendar);

            canvas.drawText(timeText, (bounds.width() / 2), timeYOffset, timePaint);

            String dateText = mAmbient
                    ? String.format(Locale.getDefault(), "%ta, %tb %td %tY", mCalendar, mCalendar, mCalendar, mCalendar)
                    : String.format(Locale.getDefault(), "%ta, %tb %td %tY", mCalendar, mCalendar, mCalendar, mCalendar);

            canvas.drawText(dateText, (bounds.width() / 2), dateYOffset, datePaint);

            if (weatherBitmap != null && !mAmbient) {
                canvas.drawBitmap(
                        weatherBitmap,
                        (bounds.width() / 2) - (weatherBitmap.getScaledWidth(canvas) + 5),
                        (bounds.height() / 2) + 20,
                        weatherPaint);
            }

            if (textMaxTemp != null && textMinTemp != null) {
                float temperatureOffsetY;
                float temperatureOffsetX;

                Rect maxTempBounds = new Rect();
                maxTempPaint.getTextBounds(textMaxTemp, 0, textMaxTemp.length(), maxTempBounds);

                if (mAmbient) {
                    temperatureOffsetY = (bounds.height() / 2) + maxTempBounds.height() + 35;
                    temperatureOffsetX = (bounds.width() / 2) - maxTempBounds.width() - 2.5f;
                } else {
                    temperatureOffsetY = (bounds.height() / 2) + maxTempBounds.height() + 35;
                    temperatureOffsetX = (bounds.width() / 2) + 10;
                }

                canvas.drawText(textMaxTemp, temperatureOffsetX, temperatureOffsetY, maxTempPaint);
                canvas.drawText(textMinTemp, temperatureOffsetX + maxTempBounds.width() + 5, temperatureOffsetY, minTempPaint);
            }
        }

        /**
         * Starts the {@link #updateWatchfaceHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            updateWatchfaceHandler.removeMessages(MSG_UPDATE_TIME);
            updateWatchfaceHandler.removeMessages(MSG_UPDATE_WEATHER);
            if (shouldTimerBeRunning()) {
                updateWatchfaceHandler.sendEmptyMessage(MSG_UPDATE_TIME);
                updateWatchfaceHandler.sendEmptyMessage(MSG_UPDATE_WEATHER);
            }
        }

        /**
         * Returns whether the {@link #updateWatchfaceHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                updateWatchfaceHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /**
         * Handle updating the weather periodically in interactive mode.
         * Although it might be very unlikely that the watchface stays in interactive mode
         * for 1 hour we still register the update-timer. Once it's put in ambient
         * mode the update messages will be removed anyways.
         */
        private void handleUpdateWeatherMessage() {
            issueWeatherUpdate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                // update once every hour
                long updateRate = INTERACTIVE_UPDATE_RATE_MS * 3600;

                long delayMs = updateRate - (timeMs % updateRate);
                updateWatchfaceHandler.sendEmptyMessageDelayed(MSG_UPDATE_WEATHER, delayMs);
            }
        }

        private void issueWeatherUpdate() {
            if (googleApiClient != null) {
                PutDataMapRequest map = PutDataMapRequest.create("/weather-update");
                map.getDataMap().putLong("seed", System.currentTimeMillis());
                map.setUrgent();
                Wearable.DataApi.putDataItem(googleApiClient, map.asPutDataRequest());
            } else {
                Log.e(TAG, "Could not issue weather-update because Google-Api Client is not present");
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "Watchface connected to Google-Api.");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Google-Api connection with id: " + i + " suspended.");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.w(TAG, "Connection to Google-Api failed: '" + connectionResult.getErrorMessage() + "'.");
        }
    }
}
