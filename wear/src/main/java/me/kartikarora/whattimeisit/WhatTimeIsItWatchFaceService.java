/**
 * Copyright 2016 Kartik Arora
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kartikarora.whattimeisit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WhatTimeIsItWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface MONO_TYPEFACE =
            Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

    public static String PREF_TIME_FORMAT = "timeformat";
    public static String PREF_AM_PM = "ampm";
    public static String PREF_DAY_DATE = "daydate";
    public static String PREF_HEX = "hex";
    public static String PREF_SECONDS = "seconds";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WhatTimeIsItWatchFaceService.Engine> mWeakReference;

        public EngineHandler(WhatTimeIsItWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WhatTimeIsItWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {


        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint, mTimePaint, mHexPaint, mDatePaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private GoogleApiClient mGoogleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(WhatTimeIsItWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(WhatTimeIsItWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();

            mTimePaint = createTextPaint(Color.parseColor("#ecf0f1"), NORMAL_TYPEFACE);
            mHexPaint = createTextPaint(Color.parseColor("#bdc3c7"), MONO_TYPEFACE);
            mDatePaint = createTextPaint(Color.parseColor("#bdc3c7"), NORMAL_TYPEFACE);
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();
                invalidateIfNecessary();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseGoogleApiClient() {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                Wearable.MessageApi.removeListener(mGoogleApiClient, onMessageReceivedListener);
                mGoogleApiClient.disconnect();
            }
        }

        private final MessageApi.MessageListener onMessageReceivedListener = new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                String path = messageEvent.getPath().split("/")[1];
                boolean data = Boolean.parseBoolean(new String(messageEvent.getData()));
                likhDoPreference(path, data);
                invalidateIfNecessary();
            }
        };

        private void invalidateIfNecessary() {
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WhatTimeIsItWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WhatTimeIsItWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WhatTimeIsItWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            float timeTextSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float hexTextSize = resources.getDimension(R.dimen.hex_text_size);
            mTimePaint.setTextSize(timeTextSize);
            mHexPaint.setTextSize(hexTextSize);
            mDatePaint.setTextSize(hexTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mHexPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            Rect timeRect = new Rect();
            Rect hexRect = new Rect();
            Rect dateRect = new Rect();

            final String dateText = new SimpleDateFormat("EEE, dd-MM-yy", Locale.ENGLISH).format(mCalendar.getTime());

            String format12 = "hh : mm";
            String format24 = "HH : mm";

            String format = (deDoPreference(PREF_TIME_FORMAT) ? format12 : format24)
                    + (deDoPreference(PREF_SECONDS) ? " : ss" : "");

            format += deDoPreference(PREF_AM_PM) && deDoPreference(PREF_TIME_FORMAT) ? " a" : "";

            final String timeText = new SimpleDateFormat(format, Locale.ENGLISH)
                    .format(mCalendar.getTime());
            final String hexText = getHex();
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                mBackgroundPaint.setColor(Color.parseColor(hexText));
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mTimePaint.getTextBounds(timeText, 0, timeText.length(), timeRect);
            canvas.drawText(timeText, bounds.centerX() - timeRect.centerX(), bounds.centerY() - timeRect.centerY(), mTimePaint);

            if (deDoPreference(PREF_HEX)) {
                mHexPaint.getTextBounds(hexText, 0, hexText.length(), hexRect);
                canvas.drawText(hexText, bounds.centerX() - hexRect.centerX(), bounds.height() - hexRect.height() - 25.0f, mHexPaint);
            }

            if (deDoPreference(PREF_DAY_DATE)) {
                mDatePaint.getTextBounds(dateText, 0, dateText.length(), dateRect);
                canvas.drawText(dateText, bounds.centerX() - dateRect.centerX(), bounds.height() - dateRect.height() - 50.0f, mDatePaint);
            }
        }

        private String getHex() {
            String hour = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            String minute = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.MINUTE));
            String second = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.SECOND));
            return "#" + hour + minute + second;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
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
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.MessageApi.addListener(mGoogleApiClient, onMessageReceivedListener);
            Log.d("Google API", "Connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e("Google API", "Not Connected");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e("error", connectionResult.getErrorMessage());
        }
    }

    private boolean deDoPreference(String prefName) {
        String PREF_FILE = getString(R.string.app_name);
        return getSharedPreferences(PREF_FILE, MODE_PRIVATE).getBoolean(prefName, true);
    }

    private void likhDoPreference(String prefName, boolean value) {
        String PREF_FILE = getString(R.string.app_name);
        SharedPreferences.Editor editor = getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(prefName, value);
        editor.apply();
    }
}
