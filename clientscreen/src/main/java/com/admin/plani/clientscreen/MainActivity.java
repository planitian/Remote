package com.admin.plani.clientscreen;


import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
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
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

    private Socket socket = null;
    private BufferedInputStream is = null;

    private static final int INITMEDIACODEC = 1;
    private static final int INITSOCKET = 2;

    private byte[] lenByte = new byte[4];
    private byte[] contentByte = new byte[1024];
    private ByteBuffer wrap = ByteBuffer.allocate(1024);
    private int anInt = 0;


    private byte[] globalSps=null;
    private byte[] globalPps=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Zprint.log(this.getClass(), "运行 ");
        executorService = Executors.newFixedThreadPool(10);

        initView();
        //实例化 工作线程
        HandlerThread worker = new HandlerThread("worker");
        worker.start();
        workerHandler = new Handler(worker.getLooper(), workerCallback);
        //添加回调 确保 surface 一定被打开
        show.getHolder().addCallback(surfaceCall);
        initFormat();
    }


    private void initView() {
        ip = findViewById(R.id.showIp);
        show = findViewById(R.id.show);
        ip.setText(IPUtils.getIPAddress(this));
    }


    public void initSocket() {
           if (socket!=null){
               return;
           }
        try {
            Log.d(TAG, "  线程 " + Thread.currentThread().getName());
            ServerSocket serverSocket = new ServerSocket(5553);
             socket = serverSocket.accept();
//            socket = new Socket("192.168.0.104", 7776);
            socket.setTcpNoDelay(true);
//            socket.setKeepAlive(true);
          /*  SocketAddress socketAddress = new InetSocketAddress("192.168.0.104", 7777);
            socket.connect(socketAddress,3000);*/
            Log.d(TAG, "  socket 运行成功");
            is = new BufferedInputStream(socket.getInputStream());
            while (true) {
                int len = readLen(is);
                System.out.println(" 头部长度 len "+len);
                if (len == -1) {
                    break;
                }
                byte[] temp = readBytes(len, is);
                if (temp == null) {
                    break;
                }
                switch (temp[0]) {
                    case 0:
                        setSps(temp);
                        break;
                    case 1:
                        setPps(temp);
                        break;
                    case 2:
                        inData(temp);
                        break;
                }
                Log.d(TAG, "读取数据  " + anInt++);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            releaseSocket();
            initSocket();
        }
    }

    //复制sps数组
    private void copySps(byte[] bytes) {
        globalSps = new byte[bytes.length];
        System.out.println(this.globalSps==null);
        System.arraycopy(bytes, 0, globalPps, 0, bytes.length);
    }

    //复制pps数组
    private void copyPps(byte[] bytes) {
        globalPps = new byte[bytes.length];
        System.arraycopy(bytes, 0, globalPps, 0, bytes.length);
    }
   //实例Format
    public void initFormat() {

        format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1080, 1920);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
//        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 0.01f);
        if (globalSps != null) {
            format.setByteBuffer("csd-0", ByteBuffer.wrap(globalSps));
        }
        if (globalPps != null) {
            format.setByteBuffer("csd-1", ByteBuffer.wrap(globalPps));
        }
        bufferInfo = new MediaCodec.BufferInfo();
    }

    public void initMediaCodec() {
        try {
            //创建解码器  注意是解码器
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (surface != null) {
                mediaCodec.configure(format, surface, null, 0);
                mediaCodec.start();
                workerHandler.sendEmptyMessage(INITSOCKET);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //设置sps
    private void setSps(byte[] sps) {
        if (format == null) {
            initFormat();
        }
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps, 1, sps.length - 1));
//        copySps(sps);
    }

    //设置pps
    private void setPps(byte[] pps) {
        if (format == null) {
            initFormat();
        }
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps, 1, pps.length));
//        copyPps(pps);
    }

    //读取四个字节，得到传来的一帧图像 数组
    public int readLen(BufferedInputStream inputStream) throws Exception {
        int len = 0;
        int count = 0;
        while (count < 4) {
            len = inputStream.read(lenByte, len, 4 - len);
            if (len == -1) {
                Log.d(TAG, "socket 关闭");
                return -1;
            }
            count += len;
        }
        for (int i = 0; i < lenByte.length; i++) {
            System.out.println(lenByte[i]);
        }
        return ByteUtils.ByteArrayToInt(lenByte);
    }

    //读取一帧图像的数组
    public byte[] readBytes(int len, BufferedInputStream inputStream) throws Exception {
        //len 包含标识字节 1位
        byte[] temp = new byte[len];

        int read = 0;
        int countByte = 0;
        while (countByte < len) {
            read = inputStream.read(temp, read, len - countByte);
            if (read == -1) {
                return null;
            }
            countByte += read;
        }
        System.out.println(" 读取的数据 "+countByte);
        return temp;
    }
    //将得到的数据 传入 mediaCodec
    public void inData(byte[] data) {
        if (mediaCodec == null||globalSps==null||globalPps==null) {
            return;
        }
        //获取到输入缓冲区的 索引
        int inputBufferID = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferID >= 0) {
            ByteBuffer inputByte = mediaCodec.getInputBuffer(inputBufferID);
//            System.out.println(">>>>>>>>>>>>>>>"+inputByte.capacity());
            inputByte.clear();
            inputByte.put(data, 1, data.length - 1);
            mediaCodec.queueInputBuffer(inputBufferID, 0, data.length, System.nanoTime() / 1000L, MediaCodec.BUFFER_FLAG_KEY_FRAME);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferID = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        if (outputBufferID >= 0) {
            mediaCodec.releaseOutputBuffer(outputBufferID, true);
        }
    }


    private void releaseMedia() {
        if (mediaCodec != null) {
            mediaCodec.release();
        }
    }

    private void releaseSocket() {
        try {
            if (is != null) {
                is.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            is = null;
            socket = null;
        }
    }

    Handler.Callback workerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case INITMEDIACODEC:
                    initMediaCodec();
                    Zprint.log(this.getClass()," initMediaCodec() ");
                    break;
                case INITSOCKET:
                    executorService.submit(() -> initSocket());
                    break;
            }

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

    SurfaceHolder.Callback surfaceCall = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Zprint.log(this.getClass(), " Surface 创建了");
            surface = holder.getSurface();
            workerHandler.sendEmptyMessage(INITMEDIACODEC);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surface = holder.getSurface();
//            workerHandler.sendEmptyMessage(INITMEDIACODEC);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            releaseMedia();
        }
    };
}
