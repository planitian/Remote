package com.admin.plani.remotescreen.utils;

import android.os.Environment;

import com.admin.plani.remotescreen.MyApplication;


/**
 * 创建时间 2018/9/4
 *
 * @author plani
 */
public class FileUtils {

   //根据url 得到存储地址
    public static String filePath(String fileName){
        String state = Environment.getExternalStorageState();
        String filePath;
        if(state.equals(Environment.MEDIA_MOUNTED)){
           filePath=Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+Environment.DIRECTORY_DOWNLOADS+"/"+fileName;
        }else {
            filePath = MyApplication.getApplication().getCacheDir().getAbsolutePath()+"/"+fileName;
        }
       return filePath;
    };











}
