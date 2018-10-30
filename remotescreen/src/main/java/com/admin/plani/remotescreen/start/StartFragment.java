package com.admin.plani.remotescreen.start;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.admin.plani.remotescreen.Permissions;
import com.admin.plani.remotescreen.R;
import com.admin.plani.remotescreen.base.BaseFragment;
import com.admin.plani.remotescreen.service.MyService;

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
    public static StartFragment newInstance() {
        Bundle args = new Bundle();
        StartFragment fragment = new StartFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.start_frag, container, false);
        start = root.findViewById(R.id.start_remote);
        start.setOnClickListener(this);
        return root;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        presenter.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroy();
    }

    @Override
    public void setPrensenter(StartContact.Presenter prensenter) {
        this.presenter = Objects.requireNonNull(prensenter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.start_remote:
                projectionManager = (MediaProjectionManager)context.getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(projectionManager.createScreenCaptureIntent(),REQUEST_CODE);
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==REQUEST_CODE&&resultCode==RESULT_OK){
//            Intent intent = new Intent(context, MyService.class);
//            data.fillIn(intent,FILL_IN_COMPONENT);
//            data.setComponent(new ComponentName(context, MyService.class));
            data.setClassName(context, MyService.class.getName());
            context.startService(data);

        }
    }

    /*private String mAction; //动作
    private Uri mData;  //用于打开资源文件
    private String mType;
    private String mPackage;  //包名
    private ComponentName mComponent;
    private int mFlags;
    private ArraySet<String> mCategories;
    private Bundle mExtras;
    private Rect mSourceBounds;
    private Intent mSelector;
    private ClipData mClipData;*/
}
