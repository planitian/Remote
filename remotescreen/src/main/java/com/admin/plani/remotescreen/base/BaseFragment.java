package com.admin.plani.remotescreen.base;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Objects;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public abstract class BaseFragment<T extends BasePresenter> extends Fragment implements BaseView<T> {
    public RetryLayout retryLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        retryLayout = new RetryLayout(getContext()) {
            @Override
            public View createContentView() {
                return createView(inflater, container, savedInstanceState);
            }

            @Override
            public OnClickListener errorClick() {
                return null;
            }
        };
        return retryLayout;
    }
    //子类重写  用于返回显示的内容view
    public abstract View createView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    @Override
    public void showDialog() {

    }

    @Override
    public void hideDialog() {

    }

    @Override
    public void error() {
      retryLayout.showError();
    }
}
