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
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import com.admin.plani.remotescreen.start.MainActivity;
import com.admin.plani.remotescreen.utils.Zprint;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Activity.RESULT_OK;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class MyService extends Service {
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private int width;
    private int height;
    private int dpi;
    private ExecutorService executorService;
    private Recorder recorder;
    //本地广播
    private LocalBroadcastManager localBroadcastManager;
    private Receiver receiver;

    //t通知
    Notification notification;
    private NotificationManager notificationManager;
    private final int NOTIID = 1;
    private final String CHANNELID = "service";

    public MyService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        width = dm.widthPixels;
        height = dm.heightPixels;
        dpi = dm.densityDpi;
        executorService = Executors.newSingleThreadExecutor();
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

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        projection = projectionManager.getMediaProjection(RESULT_OK, intent);
        recorder = new Recorder(projection);
        executorService.submit(recorder);
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
    }


    private class Recorder implements Runnable {
        private MediaProjection mProjection;
        private MediaCodec mediaCodec;
        private Surface mSurface;

        private MediaMuxer mediaMuxer;//这是可选的，这是将录屏文件 写到本地用的 实现同样的功能 MediaRecorder 也可以

        private VirtualDisplay virtualDisplay;
        private MediaCodec.BufferInfo bufferInfo;//缓冲区信息
        private AtomicBoolean atomicBoolean;//控制是否退出录制

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
                mediaMuxer = initMediaMuxer();
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
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

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
            if (local && mediaMuxer == null) {
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
            }
            while (atomicBoolean.get()) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    Zprint.log(this.getClass(), "可以录制 ");
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    // 这里 直播的话 传送 缓存数组
                    byte[] temp = new byte[outputBuffer.limit()];
                    outputBuffer.get(temp);
                    try {
                        bufferedOutputStream.write(temp);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    //录制mp4
                    encodeToVideoTrack(outputBuffer);
                    //释放输出缓冲区的数据 这样才能接受新的数据
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //输出格式变化

                    MediaFormat newOutFormat = mediaCodec.getOutputFormat();

                    ByteBuffer sps = newOutFormat.getByteBuffer("csd-0");    // SPS
                    ByteBuffer pps = newOutFormat.getByteBuffer("csd-1");    // PPS
                    Zprint.log(this.getClass(), " 输出格式 有变化 ");
                    if (mediaMuxer != null) {
                        //跟踪新的 格式的 信道
                        mVideoTrackIndex = mediaMuxer.addTrack(newOutFormat);
                        mediaMuxer.start();
                    }

                } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Zprint.log(this.getClass(), " INFO_TRY_AGAIN_LATER  ");
                /*    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
            }
            try {
                bufferedOutputStream.flush();
                bufferedOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();

            }

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

            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }
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
            } else {
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

}
