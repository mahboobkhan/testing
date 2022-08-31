package com.example.myapplication.saytimeapp;

import android.app.Application;


public class SayTimeApp extends Application {

    private static final String TAG = "SayTimeApp";
    public static TTSEngineManager mTTSEngineManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mTTSEngineManager = new TTSEngineManager(this);
    }

    @Override
    public void onTerminate() {
        mTTSEngineManager.destroy();
        super.onTerminate();
    }
}
