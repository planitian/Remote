package com.admin.plani.remotescreen.start;

/**
 * 创建时间 2018/10/30
 *
 * @author plani
 */
public class StartPresenter implements StartContact.Presenter {
    private StartContact.View view;

    @Override
    public void setView(StartContact.View view) {
        if (view!=null){
            this.view = view;
            this.view.setPrensenter(this);
        }else {
            throw new NullPointerException("view 为空");
        }
    }

    @Override
    public void start() {

    }

    @Override
    public void destroy() {

    }
}
