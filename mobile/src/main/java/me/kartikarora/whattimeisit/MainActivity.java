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

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private Calendar mCalendar;
    private Timer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.settings_image_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ConfigurationActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                mCalendar = Calendar.getInstance();
                mCalendar.setTimeInMillis(now);

                final String dateText = new SimpleDateFormat("EEE, dd-MM-yy", Locale.ENGLISH).format(mCalendar.getTime());

                String format12 = "hh : mm";
                String format24 = "HH : mm";

                String format = (deDoPreference(ConfigurationActivity.PREF_TIME_FORMAT) ? format12 : format24)
                        + (deDoPreference(ConfigurationActivity.PREF_SECONDS) ? " : ss" : "");

                format += deDoPreference(ConfigurationActivity.PREF_AM_PM) && deDoPreference(ConfigurationActivity.PREF_TIME_FORMAT) ? " a" : "";

                final String timeText = new SimpleDateFormat(format, Locale.ENGLISH)
                        .format(mCalendar.getTime());
                final String hexText = getHex();

                if (!deDoPreference(ConfigurationActivity.PREF_HEX))
                    findViewById(R.id.hex_text_view).setVisibility(View.INVISIBLE);

                if (!deDoPreference(ConfigurationActivity.PREF_DAY_DATE))
                    findViewById(R.id.day_text_view).setVisibility(View.INVISIBLE);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.time_text_view)).setText(timeText);
                        ((TextView) findViewById(R.id.hex_text_view)).setText(hexText);
                        ((TextView) findViewById(R.id.day_text_view)).setText(dateText);
                        findViewById(R.id.activity_main).setBackgroundColor(Color.parseColor(hexText));
                    }
                });
            }
        }, 0, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTimer.cancel();
    }

    private String getHex() {
        String hour = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
        String minute = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.MINUTE));
        String second = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.SECOND));
        return "#" + hour + minute + second;
    }

    private boolean deDoPreference(String prefName) {
        String PREF_FILE = getString(R.string.app_name);
        return getSharedPreferences(PREF_FILE, MODE_PRIVATE).getBoolean(prefName, true);
    }
}
