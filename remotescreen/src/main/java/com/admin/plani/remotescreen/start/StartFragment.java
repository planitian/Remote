package com.admin.plani.remotescreen.start;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.admin.plani.remotescreen.Permissions;
import com.admin.plani.remotescreen.R;
import com.admin.plani.remotescreen.base.BaseFragment;
import com.admin.plani.remotescreen.service.MyService;
import com.admin.plani.remotescreen.utils.Zprint;

import java.util.Map;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.content.Intent.FILL_IN_ACTION;
import static android.content.Intent.FILL_IN_COMPONENT;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public class StartFragment extends BaseFragment<StartContact.Presenter> implements StartContact.View ,View.OnClickListener {

    private Button start;

    private StartContact.Presenter presenter;


    private final int REQUEST_CODE = 2;

    private MediaProjectionManager projectionManager;

    private Context context;
    //用来判断 开始 还是 暂停
    private int pause = 0;
    //用于存储上面变量的key
    private final String pauseKey = "pause";

    /**
     * @param extra 用于要传入给fragment的值
     * @return
     */
    public static StartFragment newInstance(Map<String,Integer> extra) {
        Bundle args = new Bundle();
        //进行赋值
        for (Map.Entry<String,Integer> entry:extra.entrySet()){
            args.putInt(entry.getKey(), entry.getValue());
        }
        StartFragment fragment = new StartFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * @return 返回一个 没有参数的fragment
     */
   public static StartFragment newInstance() {
        StartFragment fragment = new StartFragment();
        return fragment;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        Zprint.log(this.getClass(),"运行");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Zprint.log(this.getClass(),"运行");
        super.onCreate(savedInstanceState);
        if (savedInstanceState!=null){
            pause = savedInstanceState.getInt(pauseKey);
        }
    }

    @Override
    public View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Zprint.log(this.getClass(),"运行");
        View root = inflater.inflate(R.layout.start_frag, container, false);
        start = root.findViewById(R.id.start_remote);
        start.setOnClickListener(this);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Zprint.log(this.getClass(),"运行");
        presenter.start();
    }

    @Override
    public void onStart() {
        super.onStart();
        Zprint.log(this.getClass(),"运行");
    }

    @Override
    public void onResume() {
        super.onResume();
        Zprint.log(this.getClass(),"运行");
    }

    @Override
    public void onPause() {
        super.onPause();
        Zprint.log(this.getClass(),"运行");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Zprint.log(this.getClass(),"运行");
        presenter.destroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Zprint.log(this.getClass(),"运行");
        outState.putInt(pauseKey,pause);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==REQUEST_CODE&&resultCode==RESULT_OK){
          /*  Intent intent = new Intent(context, MyService.class);
            data.fillIn(intent,FILL_IN_COMPONENT);*/
//            data.setComponent(new ComponentName(context, MyService.class));
            data.setClassName(context, MyService.class.getName());
            context.startService(data);
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                context.startForegroundService(data);
//            } else {
//                context.startService(data);
//            }
        }
    }


    @Override
    public void setPrensenter(StartContact.Presenter prensenter) {
        this.presenter = Objects.requireNonNull(prensenter);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.start_remote:
                if ((pause%2)==0) {
                    projectionManager = (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
                    start.post(new Runnable() {
                        @Override
                        public void run() {
                          start.setText("暂停录制");
                        }
                    });
                }else {
                    Intent intent = new Intent("pause_recodr");
                    intent.putExtra("pause", true);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    start.post(new Runnable() {
                        @Override
                        public void run() {
                            start.setText("开始推送屏幕");
                        }
                    });
                }
                pause++;
                break;
        }
    }

}
