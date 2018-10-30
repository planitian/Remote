package com.admin.plani.remotescreen.start;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.admin.plani.remotescreen.R;
import com.admin.plani.remotescreen.utils.ActivityUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        StartFragment startFragment = (StartFragment) getSupportFragmentManager().findFragmentById(R.id.start_content);
        if (startFragment==null){
            startFragment = StartFragment.newInstance();
            ActivityUtils.addFragmentToActivity(getSupportFragmentManager(),startFragment,R.id.start_content);
        }
        new StartPresenter().setView(startFragment);
    }

}
