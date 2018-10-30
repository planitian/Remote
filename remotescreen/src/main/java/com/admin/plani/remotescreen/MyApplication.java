package com.admin.plani.remotescreen;

import android.app.Application;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public class MyApplication extends Application {
    private static MyApplication myApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        myApplication = MyApplication.this;
    }

    public static MyApplication getApplication(){
        return myApplication;
    }
}
