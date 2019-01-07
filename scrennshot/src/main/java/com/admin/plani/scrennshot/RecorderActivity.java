package com.admin.plani.scrennshot;

import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Button;

import java.io.IOException;
import java.util.Objects;

public class RecorderActivity extends AppCompatActivity {
    final   int    REQUSET = 10;
    private Button start;
    MediaProjectionManager projectionManager;//变成全局变量，是因为 不止在一个方法里面使用到

    private int width;//屏幕宽度
    private int height;//屏幕高度
    private int dpi;//dpi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);
        //获取屏幕宽高
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        width = point.x;
        height = point.y;
        //获取屏幕DPI
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.densityDpi;

        start = findViewById(R.id.button);
        start.setOnClickListener(v -> {
            //获取MediaProjectionManager ，通过这个类 申请权限，
            // 录屏是一个危险的权限，所以每次录屏的时候都得这么申请，用户同意了才行
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            //开启activity 然后在onActivityResult回调里面判断 用户是否同意录屏权限
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUSET);
        });

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUSET && resultCode == RESULT_OK) {//已经成功获取到权限
            MediaProjection projection = projectionManager.getMediaProjection(resultCode, data);
            startRecorderWithMediaRecorder(projection);
        }
    }


    /**
     * 录屏方式一，使用 MediaRecorder类
     * 这种方式 可以录制视频到本地，但是不能取得视频流，无法进行直播
     *
     * @param projection
     */
    void startRecorderWithMediaRecorder(MediaProjection projection) {
        Objects.requireNonNull(projection);
        //这个呢 一定要注意 文件名后面有 文件格式，并且要和下面你录制视频的格式相同
        String filePath = filePath("mediaRecorder.mp4");
        MediaRecordThread mediaRecordThread = new MediaRecordThread(720,1080,60000,dpi,projection,filePath,1);
        mediaRecordThread.start();
    }
    //录制视频 放在子线程最好，所以卸载
    class MediaRecordThread extends Thread {
        private       int             mWidth;//录制视频的宽
        private       int             mHeight;//录制视频的高
        private       int             mBitRate;//比特率 举个例子100  代表1秒钟 要有100K的数据，比特率越高 视频越清晰
        private       int             mDpi;//视频的DPI
        private       String          mDstPath;//录制视频文件存放地点
        private       MediaRecorder   mediaRecorder;//通过这个类录制
        private       MediaProjection mediaProjection;//通过这个类 生成虚拟屏幕
        private final int             FRAME_RATE = 60;//视频帧数 一秒多少张画面
        private long mTime;//录制时间  以分钟为单位
        private VirtualDisplay virtualDisplay;

        public MediaRecordThread(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath,int time) {
            mWidth = width;
            mHeight = height;
            mBitRate = bitrate;
            mDpi = dpi;
            this.mediaProjection = mediaProjection;
            mDstPath = dstPath;
            mTime = time * 10 & 1000;
        }

        @Override
        public void run() {
            try {
                //先实例化
                initMediaRecorder();
                //下面这个方法的 width height  并不是录制视频的宽高。他更明显是虚拟屏幕的宽高
                //注意 mediaRecorder.getSurface() 这里我们mediaRecorder的surface 传递给虚拟屏幕，
                // 虚拟屏幕显示的内容就会反映在这个surface上面，自然也就可以录制了
                virtualDisplay = mediaProjection.createVirtualDisplay("luing", mWidth, mHeight, mDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mediaRecorder.getSurface(), null, null
                );
                //开始
                mediaRecorder.start();
                long brfore = System.currentTimeMillis();
                while ((System.currentTimeMillis()-brfore)<mTime){
                    System.out.println(">>>>>>>>>>");
                }
                Zprint.log(this.getClass(), "录屏线程内部开始工作");
            } catch (IllegalStateException |IOException e) {
                e.printStackTrace();
            } finally {
                release();
            }
        }
        //实例化MediaRecordor
        void initMediaRecorder() throws IOException {
            mediaRecorder = new MediaRecorder();
            //设置视频来源  录屏嘛 肯定是使用一个Surface作为视频源，如果录制视频的话 就是使用摄像头作为来源了 CAMERA
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            //设置要用于录制的音频源,必须在setOutputFormat()之前调用。参数有很多，英文注释也很简单，下面这个是录制麦克风，也就是外放的
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //设置录制期间生成的输出文件的格式。必须在prepare()之前调用
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //录制文件存放位置
            mediaRecorder.setOutputFile(mDstPath);
            //录制视频的宽高
            mediaRecorder.setVideoSize(mWidth, mHeight);
            //FPS
            mediaRecorder.setVideoFrameRate(FRAME_RATE);
            //比特路
            mediaRecorder.setVideoEncodingBitRate(mBitRate);
            //视频编码格式
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //音频编码格式
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                //准备 到这里 就可以开始录制视频了
                mediaRecorder.prepare();
        }

        /**
         * 释放资源
         */
        void release() {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;//help GC
            }
            if (mediaRecorder != null) {
                mediaRecorder.setOnErrorListener(null);
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;//help GC
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;//help GC
            }
        }
    }


    /**
     * @param fileName 文件名字
     * @return 文件的地址，默认在recorder文件夹下
     */
    public  String filePath(String fileName){
        String state = Environment.getExternalStorageState();
        String filePath;
        if(state.equals(Environment.MEDIA_MOUNTED)){
            filePath=Environment.getExternalStorageDirectory().getAbsolutePath()+"/recorder/"+fileName;
        }else {
            filePath = MyApplication.getApplication().getCacheDir().getAbsolutePath()+"/recorder/"+fileName;
        }
        return filePath;
    };
}
