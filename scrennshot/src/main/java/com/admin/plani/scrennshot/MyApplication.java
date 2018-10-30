package com.admin.plani.scrennshot;

import android.app.Application;

/**
 * 创建时间 2018/10/23
 *
 * @author plani
 */
public class MyApplication extends Application {

    private static   MyApplication myApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        myApplication = this;
    }

    public static  MyApplication getApplication(){
        return myApplication;
    }
}
