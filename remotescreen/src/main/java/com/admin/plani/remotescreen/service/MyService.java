package com.admin.plani.remotescreen.service;

import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;

import com.admin.plani.remotescreen.utils.Zprint;

import static android.app.Activity.RESULT_OK;

public class MyService extends Service {
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;

    public MyService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Zprint.log(this.getClass()," shilihua");
        projection = projectionManager.getMediaProjection(RESULT_OK, intent);
        Zprint.log(this.getClass()," projection ",projection);
        return super.onStartCommand(intent, flags, startId);
    }



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }



}
