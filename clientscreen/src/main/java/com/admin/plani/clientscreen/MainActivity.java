package com.admin.plani.clientscreen;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView ip;
    private SurfaceView show;

    private MediaCodec mediaCodec;
    private Surface surface;
    private MediaCodec.BufferInfo bufferInfo;
    private MediaFormat format;
    private Handler workerHandler;
    Handler mainHandler = new Handler(new mainHandler());
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Zprint.log(this.getClass(), "运行 ");
        executorService = Executors.newFixedThreadPool(10);

        ip = findViewById(R.id.showIp);
        show = findViewById(R.id.show);

        ip.setText(getIPAddress(this));
        HandlerThread worker = new HandlerThread("worker");
        worker.start();
        workerHandler = new Handler(worker.getLooper(), workerCallback);

        show.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surface = holder.getSurface();
                initMediaCodec();
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
            /*    Future<Boolean> result= executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        initSocket();
                    }
                }, false);*/
                        initSocket();
                    }
                });
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });


    }


    public void initSocket() {
        try {
            Log.d(TAG, "  线程 " + Thread.currentThread().getName());
            ServerSocket serverSocket = new ServerSocket(9936);
            Socket socket = serverSocket.accept();
            InputStream is = socket.getInputStream();

            Log.d(TAG, "  socket 运行成功");
            boolean isExit = false;
            int spsLen = readLen(is);
            byte[] sps = readBytes(spsLen, is);
            int ppsLen = readLen(is);
            byte[] pps = readBytes(ppsLen, is);
            setSpsAndPPs(sps, pps);
            while (!isExit) {
                System.out.println("》》》》读取数据");
                int len = readLen(is);
                byte[] temp = readBytes(len, is);
                inData(temp);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        Zprint.log(this.getClass(), "运行 ");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Zprint.log(this.getClass(), "运行 ");
    }

    public void initMediaCodec() {
        format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1080, 1920);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        bufferInfo = new MediaCodec.BufferInfo();
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (surface != null) {
                mediaCodec.configure(format, surface, null, 0);
                mediaCodec.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //设置sps  和pps
    public void setSpsAndPPs(byte[] sps, byte[] pps) {
        if (format == null) {
            return;
        }
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
    }

    //读取四个字节，得到传来的一帧图像 数组
    public int readLen(InputStream inputStream) throws Exception {
        byte[] temp = new byte[4];
        int len = 4;
        for (int i = 0; i < len; i++) {
            int date = inputStream.read();
            if (date==-1){
                throw new IllegalAccessException("流结束了");
            }
            temp[i] = (byte) date;
        }
        return ByteUtils.ByteArrayToInt(temp);
    }

    //读取一帧图像的数组
    public byte[] readBytes(int len, InputStream inputStream) throws Exception {
        byte[] temp = new byte[len];
        for (int i = 0; i <len ; i++) {
            temp[i] = (byte) inputStream.read();
        }
        return temp;
    }

    public void inData(byte[] data) {
        if (mediaCodec == null) {
            return;
        }
        //获取到输入缓冲区的 索引
        int inputBufferID = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferID >= 0) {
            ByteBuffer inputByte = mediaCodec.getInputBuffer(inputBufferID);
            inputByte.clear();
            inputByte.put(data);
            mediaCodec.queueInputBuffer(inputBufferID, 0, data.length, System.nanoTime() / 1000L, MediaCodec.BUFFER_FLAG_SYNC_FRAME);
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferID = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        if(outputBufferID>=0) {
            mediaCodec.releaseOutputBuffer(outputBufferID, true);
        }
        System.out.println("》》》》解析");
    }

    Handler.Callback workerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            return true;
        }
    };


    class mainHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    ip.setText(ip.getText().toString().trim() + "  socket链接成功");
                    break;
            }
            return true;
        }
    }


    public static String getIPAddress(Context context) {
        NetworkInfo info = ((ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//当前使用2G/3G/4G网络
                try {
                    //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }

            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//当前使用无线网络
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());//得到IPV4地址
                return ipAddress;
            }
        } else {
            //当前无网络连接,请在设置中打开网络
        }
        return null;
    }

    public static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }
}
