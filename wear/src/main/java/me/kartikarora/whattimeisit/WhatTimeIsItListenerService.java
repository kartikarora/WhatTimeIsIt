package me.kartikarora.whattimeisit;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;


/**
 * Developer: chipset
 * Package : me.kartikarora.whattimeisit
 * Project : WhatTimeIsit
 * Date : 10/28/16
 */

public class WhatTimeIsItListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath().split("/")[1];
        boolean data = Boolean.parseBoolean(new String(messageEvent.getData()));
        Log.d(path, data + "");
        likhDoPreference(path, data);
        super.onMessageReceived(messageEvent);
    }

    private void likhDoPreference(String prefName, boolean value) {
        String PREF_FILE = getString(R.string.app_name);
        SharedPreferences.Editor editor = getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit();
        editor.putBoolean(prefName, value);
        editor.apply();
    }
}
