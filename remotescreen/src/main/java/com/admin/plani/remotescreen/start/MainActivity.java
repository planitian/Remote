package com.admin.plani.remotescreen.start;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;

import com.admin.plani.remotescreen.R;
import com.admin.plani.remotescreen.utils.ActivityUtils;
import com.admin.plani.remotescreen.utils.Zprint;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private AlertDialog alertDialog;

    private int width;
    private int height;
    private int dpi;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Zprint.log(this.getClass(),"运行");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StartFragment startFragment = (StartFragment) getSupportFragmentManager().findFragmentById(R.id.start_content);
        if (startFragment==null){
            startFragment = StartFragment.newInstance();
            ActivityUtils.addFragmentToActivity(getSupportFragmentManager(),startFragment,R.id.start_content);
        }
        new StartPresenter().setView(startFragment);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        Zprint.log(this.getClass(),"运行");
        super.onStart();
        if (!isPermission()) {
            obtainPermission();
        }
    }

    @Override
    protected void onResume() {
        Zprint.log(this.getClass(),"运行");
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Zprint.log(this.getClass(),"运行");
    }

    //是否拥有权限
    public boolean isPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission
                .WRITE_EXTERNAL_STORAGE);
        return permission == PackageManager.PERMISSION_GRANTED;
    }

    //获取权限
    public void obtainPermission() {
        alertDialog = new AlertDialog.Builder(this).setTitle("权限申请").setMessage
                ("给app开启存储空间权限，注意你不开启，app将退出").setPositiveButton("确定", new DialogInterface
                .OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                requestPermission();
            }
        }).setNegativeButton("退出app", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
                System.exit(0);
            }
        }).show();
    }

    //请求权限
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission
                .WRITE_EXTERNAL_STORAGE}, 1);
    }

    public void initWidthHeightDpi(){
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        width = point.x;
        height = point.y;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.densityDpi;
        System.out.println("width height dpi " + width + "    " + height + "    " + dpi);
    }
}
