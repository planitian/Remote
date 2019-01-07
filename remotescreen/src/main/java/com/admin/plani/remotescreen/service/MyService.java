package com.admin.plani.remotescreen.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.admin.plani.remotescreen.start.MainActivity;
import com.admin.plani.remotescreen.utils.ByteUtils;
import com.admin.plani.remotescreen.utils.SocketConnect;
import com.admin.plani.remotescreen.utils.Zprint;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Activity.RESULT_OK;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class MyService extends Service {
    private MediaProjectionManager projectionManager;
    private MediaProjection        projection;
    private int                    width;
    private int                    height;
    private int                    dpi;
    //线程池
    private ExecutorService        executorService;
    //工作线程
    private Handler                worker;

    private Recorder              recorder;
    //本地广播
    private LocalBroadcastManager localBroadcastManager;
    private Receiver              receiver;

    //t通知
    Notification notification;
    private       NotificationManager notificationManager;
    private final int                 NOTIID    = 1;
    private final String              CHANNELID = "service";
    private       ExecutorService     service;

    //socket
    private Socket socket;

    private BufferedOutputStream outputStream;
    private BufferedInputStream  inputStream;

    private int anInt = 1;

    private static final int SPS = 0;
    private static final int PPS = 1;
    private static final int FRA = 2;

    private int count = 0;
    public MyService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        width = (int) (dm.widthPixels / 1.5);
        height = (int) (dm.heightPixels / 1.5);
        dpi = (int) (dm.densityDpi / 1.5);
        //本地广播
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        receiver = new Receiver();
        IntentFilter intentFilter = new IntentFilter("pause_recodr");
        localBroadcastManager.registerReceiver(receiver, intentFilter);


        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(CHANNELID, "后台服务", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }


        //前台通知
        notification = initNotification("等待中");
        startForeground(NOTIID, notification);

        //实例化 线程池
        executorService = Executors.newFixedThreadPool(10);

        HandlerThread handlerThread = new HandlerThread("worker");
        handlerThread.start();
        worker = new Handler(handlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        executorService.submit(recorder);
                        break;
                }
                return true;
            }
        });

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        projection = projectionManager.getMediaProjection(RESULT_OK, intent);
        recorder = new Recorder(projection);
        executorService.submit(() -> initSocket());
        Zprint.log(this.getClass(), " projection ", projection);
        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        localBroadcastManager.unregisterReceiver(receiver);
        executorService.shutdown();
        closeSocket();
    }


    private class Recorder implements Runnable {
        private MediaProjection mProjection;
        private MediaCodec      mediaCodec;
        private Surface         mSurface;

        private MediaMuxer mediaMuxer;//这是可选的，这是将录屏文件 写到本地用的 实现同样的功能 MediaRecorder 也可以

        private VirtualDisplay        virtualDisplay;
        private MediaCodec.BufferInfo bufferInfo;//缓冲区信息
        private AtomicBoolean         atomicBoolean;//控制是否退出录制

        private int mVideoTrackIndex; //标识 mediaformat的 信道

        public Recorder(MediaProjection mProjection) {
            this.mProjection = mProjection;
            atomicBoolean = new AtomicBoolean(true);
            this.bufferInfo = new MediaCodec.BufferInfo();
        }

        @Override
        public void run() {
            try {
                notification = initNotification("推送中");
                notificationManager.notify(NOTIID, notification);
                prepereEncoder();
//                mediaMuxer = initMediaMuxer();
                //如果想更改录制视频的分辨率 可以在这里更改
                virtualDisplay = mProjection.createVirtualDisplay("MyService", width, height,
                        dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null);
                recordVirtualDisplay(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            notification = initNotification("等待中");
            notificationManager.notify(NOTIID, notification);
        }

        //退出
        public void exit() {
            atomicBoolean.set(false);
        }

        //准备好编码器 得到编码器的surface 这样就不需要 我们对输入缓冲区 进行写数据了
        private void prepereEncoder() throws IOException {
            //这里的宽度，高度 是从左上角原点 开始算， 如果 没有大于 virtualDisplay的宽高，只会录制 一部分内容
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mediaCodec.createInputSurface();//需要在createEncoderByType之后和start()之前才能创建，源码注释写的很清楚
            mediaCodec.start();
        }

        //如果 要录制mp4文件的话，需要调用这个方法 创建 MediaMuxer
        private MediaMuxer initMediaMuxer() throws Exception {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "service.mp4";
            File out = new File(path);
            return new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        /**
         * @param local 标识 要不要同时录制mp4文件到本机存储
         */
        private void recordVirtualDisplay(Boolean local) {
    /*        if (local && mediaMuxer == null) {
                throw new NullPointerException("如果想录制mp4到本机，必须先调用 initMediaMuxer（）");
            }
            File file = new File("/storage/emulated/0/Download/", "ss.apk");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            BufferedOutputStream bufferedOutputStream = null;
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }*/

            while (atomicBoolean.get()) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                if (outputBufferId >= 0) {
//                    Zprint.log(this.getClass(), "可以录制 ");
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);

                  /*  try {
                        bufferedOutputStream.write(temp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/

                    // 这里 直播的话 传送 缓存数组
                    byte[] temp = new byte[outputBuffer.limit()];
                    outputBuffer.get(temp);

                    sendBytes(temp, FRA);

                    //录制mp4
//                    encodeToVideoTrack(outputBuffer);
                    //释放输出缓冲区的数据 这样才能接受新的数据
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //输出格式变化

                    MediaFormat newOutFormat = mediaCodec.getOutputFormat();

                    ByteBuffer sps = newOutFormat.getByteBuffer("csd-0");    // SPS
                    ByteBuffer pps = newOutFormat.getByteBuffer("csd-1");    // PPS
                    if (sps.hasArray()) {
                        sendBytes(sps.array(), SPS);
                        sendBytes(pps.array(), PPS);
                    }
                    Zprint.log(this.getClass(), " 输出格式 有变化 ");
                 /*   if (mediaMuxer != null) {
                        //跟踪新的 格式的 信道
                        mVideoTrackIndex = mediaMuxer.addTrack(newOutFormat);
                        mediaMuxer.start();
                    }*/
                } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                    Zprint.log(this.getClass(), " INFO_TRY_AGAIN_LATER  ");
                /*    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
            }
          /*  try {
                bufferedOutputStream.flush();
                bufferedOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();

            }*/

            //告诉编码器 结束流
            mediaCodec.signalEndOfInputStream();
            mediaCodec.stop();
            //释放
            mediaCodec.release();
            mediaCodec = null;

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

          /*  if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }*/
        }


        /**
         * @param output 输出缓冲区的字节缓存
         */
        private void encodeToVideoTrack(ByteBuffer output) {
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.
                // Ignore it.
                bufferInfo.size = 0;
            }
            if (bufferInfo.size == 0) {
                output = null;
            }
            if (output != null) {
                output.position(bufferInfo.offset);
                output.limit(bufferInfo.offset + bufferInfo.size);
                mediaMuxer.writeSampleData(mVideoTrackIndex, output, bufferInfo);//写入mp4
            }
        }
    }

    class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra("pause", false)) {
                if (recorder != null) {
                    recorder.exit();
                }
            }
        }
    }


    private Notification initNotification(String content) {

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNELID)
                .setSmallIcon(getApplicationContext().getApplicationInfo().icon)
                .setWhen(System.currentTimeMillis()).setAutoCancel(true)
                .setContentText(content)
                .setContentTitle("")
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_input_add))
                .setContentIntent(pendingIntent)
                .build();
        return notification;
    }

    private void initSocket() {
        SocketConnect socketConnect = new SocketConnect("192.168.0.107", 9937);
        Future<Socket> future = executorService.submit(socketConnect);
        try {
            Socket temp = future.get();
            if (temp != null) {
                if (socket != null) {
                    if (socket.isConnected()) {
                        socket.close();
                    }
                    //回收原本的内存空间
                    socket = null;
                }
                socket = temp;
                outputStream = new BufferedOutputStream(socket.getOutputStream());
                inputStream = new BufferedInputStream(socket.getInputStream());
                Zprint.log(this.getClass(), " socket 链接成功");
                worker.sendEmptyMessage(1);
            } else {
                Zprint.log(this.getClass(), " socket 链接失败 重试");
                initSocket();
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSocket() {
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param target 要发送的数组
     * @param type   数组内容的类型 是SPS 还是PPS  FRA
     */
    public void sendBytes(byte[] target, int type) {
        if (socket == null || socket.isClosed() || outputStream == null) {
            return;
        }
        if (count>100&&count<200){
            System.out.println(">>>>>>>>>>>>>>");
            return;
        }
        count++;
        //发送数组的长度  本身长度 加标识长度 1字节
        int len = target.length + 1;
        //长度写入一个4字节的数组中
        byte[] lenBytes = ByteUtils.IntToByteArray(len);

        //将4 字节的数组进行 扩容  加上发送数组的长度和 标识长度
        byte[] endBytes = Arrays.copyOf(lenBytes, len + lenBytes.length);

        endBytes[4] = (byte) type;
        //将发送数组 的内容 写入 新数组
        System.arraycopy(target, 0, endBytes, lenBytes.length + 1, len - 1);
        try {
            System.out.println(" 图像数组读取 " + len + "   " + " 数组类型 " + type + "  发送数组 最后一位数据 " + endBytes[endBytes.length - 1]);
            outputStream.write(endBytes);
            outputStream.flush();
            Log.d(" 第几次数据", anInt++ + "");
        } catch (IOException e) {
            e.printStackTrace();
            closeSocket();
            initSocket();
        }
    }
}
