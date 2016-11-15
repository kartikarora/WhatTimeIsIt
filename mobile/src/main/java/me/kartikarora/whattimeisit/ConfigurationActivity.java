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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.CompoundButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class ConfigurationActivity extends AppCompatActivity {

    public static String PREF_TIME_FORMAT = "timeformat";
    public static String PREF_AM_PM = "ampm";
    public static String PREF_DAY_DATE = "daydate";
    public static String PREF_HEX = "hex";
    public static String PREF_SECONDS = "seconds";

    private GoogleApiClient mGoogleApiClient;
    private CoordinatorLayout mLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mLayout = (CoordinatorLayout) findViewById(R.id.activity_configuration);

        SwitchCompat timeFormatSwitchCompat = (SwitchCompat) findViewById(R.id.time_format_switch);
        final SwitchCompat amPmSwitchCompat = (SwitchCompat) findViewById(R.id.am_pm_switch);
        SwitchCompat dayDateSwitchCompat = (SwitchCompat) findViewById(R.id.day_date_switch);
        SwitchCompat secondSwitchCompat = (SwitchCompat) findViewById(R.id.second_switch);
        SwitchCompat hexSwitchCompat = (SwitchCompat) findViewById(R.id.hex_switch);

        timeFormatSwitchCompat.setChecked(deDoPreference(PREF_TIME_FORMAT));
        amPmSwitchCompat.setChecked(deDoPreference(PREF_AM_PM));
        dayDateSwitchCompat.setChecked(deDoPreference(PREF_DAY_DATE));
        secondSwitchCompat.setChecked(deDoPreference(PREF_SECONDS));
        hexSwitchCompat.setChecked(deDoPreference(PREF_HEX));

        amPmSwitchCompat.setEnabled(deDoPreference(PREF_TIME_FORMAT));

        timeFormatSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                likhDoPreference(PREF_TIME_FORMAT, isChecked);
                sendMessageToWear(PREF_TIME_FORMAT, String.valueOf(isChecked));
                amPmSwitchCompat.setEnabled(isChecked);
            }
        });

        amPmSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                likhDoPreference(PREF_AM_PM, isChecked);
                sendMessageToWear(PREF_AM_PM, String.valueOf(isChecked));
            }
        });

        dayDateSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                likhDoPreference(PREF_DAY_DATE, isChecked);
                sendMessageToWear(PREF_DAY_DATE, String.valueOf(isChecked));
            }
        });

        secondSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                likhDoPreference(PREF_SECONDS, isChecked);
                sendMessageToWear(PREF_SECONDS, String.valueOf(isChecked));
            }
        });
        hexSwitchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                likhDoPreference(PREF_HEX, isChecked);
                sendMessageToWear(PREF_HEX, String.valueOf(isChecked));
            }
        });
    }

    private void likhDoPreference(String prefName, boolean value) {
        String PREF_FILE = getString(R.string.app_name);
        SharedPreferences.Editor editor = getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(prefName, value);
        editor.apply();
        Snackbar.make(mLayout, "Configuration Saved", Snackbar.LENGTH_SHORT).show();
    }

    private boolean deDoPreference(String prefName) {
        String PREF_FILE = getString(R.string.app_name);
        return getSharedPreferences(PREF_FILE, MODE_PRIVATE).getBoolean(prefName, true);
    }

    private void sendMessageToWear(final String setting, final String value) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "setting/" + setting, value.getBytes()).await();
                    if (!result.getStatus().isSuccess()) {
                    } else {
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(mLayout, "Configuration Synced", Snackbar.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d("Google API", "Connected");
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.e("Google API", "Not Connected");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.e("error", result.getErrorMessage());
                    }
                })
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        mGoogleApiClient.disconnect();
        super.onPause();
    }
}