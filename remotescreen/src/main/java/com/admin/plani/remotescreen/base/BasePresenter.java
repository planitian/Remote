package com.admin.plani.remotescreen.base;

import com.admin.plani.remotescreen.base.BaseView;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public interface BasePresenter<T extends BaseView> {
    void setView(T view);

    //启动
    void start();

    //释放资源  Rxjava引用
    void destroy();
}
