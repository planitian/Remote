package com.admin.plani.remotescreen.base;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public interface BaseView<T extends BasePresenter> {
    void setPrensenter(T prensenter);

    void showDialog();

    void hideDialog();

    void error();
}
