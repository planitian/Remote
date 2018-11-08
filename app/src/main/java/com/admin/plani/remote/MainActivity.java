package com.admin.plani.remote;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {
    private TextView textView;
    private Button button;

      MainHandler mainHandler = new MainHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.text);
        button = findViewById(R.id.button);
        HandlerThread handlerThread = new HandlerThread("first");
        handlerThread.start();

        final Handler handler = new Handler(handlerThread.getLooper(),new HandlerCallback()) {
            int count = 0;
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                System.out.println("子线程 的handler 接受到数据  现在 将数据发送给 主线程 执行 "+Thread.currentThread().getName());
                Toast.makeText(MainActivity.this,"haahh",Toast.LENGTH_LONG).show();
                textView.setText(">>>>>>");
                Message.obtain(mainHandler, 1, ++count).sendToTarget();
            }
        };
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

             new Thread(new Runnable() {
                 @Override
                 public void run() {
                     System.out.println("子线程发送数据 "+Thread.currentThread().getName());
                     handler.sendEmptyMessage(1);
                 }
             }).start();
            }
        });
    }

    static    class MainHandler extends Handler{
        private WeakReference<MainActivity> mainActivityWeakReference;

        MainHandler(MainActivity mainActivity) {
            this.mainActivityWeakReference =new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mainActivityWeakReference.get().textView.setText(msg.obj+"");
        }
    }

    //
    private class  HandlerCallback implements Handler.Callback{

        @Override
        public boolean handleMessage(Message msg) {
            System.out.println("我是handler Callback "+Thread.currentThread().getName());
            return false;
        }
    }

}
