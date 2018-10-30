package com.admin.plani.remotescreen.base;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.admin.plani.remotescreen.MyApplication;
import com.admin.plani.remotescreen.R;

/**
 * 用于包裹fragment的内容，方便实现加载失败时候，显示重试的动画
 * 创建时间 2018/9/17
 *
 * @author plani
 */
public abstract class RetryLayout extends FrameLayout {
    private View errorView;
    private View contentView;
    public RetryLayout(@NonNull Context context) {
        super(context);
        initView();
    }

    public RetryLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public void initView(){
        contentView=createContentView();
        errorView = createErrorView();

        this.addView(contentView,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        LayoutParams errParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        errParams.gravity = Gravity.CENTER;
        this.addView(errorView,errParams);

        errorView.setVisibility(GONE);

    }
    //返回真正显示的view
    public abstract View createContentView();

    //创建error视图
    private View createErrorView(){
        View view = LayoutInflater.from(MyApplication.getApplication()).inflate(R.layout.base_no_internet, null);
        Button error=view.findViewById(R.id.refresh_internet);
        error.setOnClickListener(errorClick());
        return view;
    }

    //返回错误 重试的点击事件
    public abstract OnClickListener errorClick();

    //显示内容界面
    public void showContent(){
        contentView.setVisibility(VISIBLE);
        if (errorView.isShown()){
            errorView.setVisibility(GONE);
        }
    }

    //显示网络错误界面
    public void showError(){
        errorView.setVisibility(VISIBLE);
       if (contentView.isShown()){
           contentView.setVisibility(GONE);
       }
    }
}
