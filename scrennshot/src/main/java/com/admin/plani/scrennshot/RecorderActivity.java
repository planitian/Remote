package com.admin.plani.scrennshot;

import android.Manifest;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class RecorderActivity extends AppCompatActivity {
    final int REQUSET_VIDEO = 10;
    final int REQUSET_AUDIO = 11;
    private Button start;
    private Button pause;
    MediaProjectionManager projectionManager;//变成全局变量，是因为 不止在一个方法里面使用到

    private int width;//屏幕宽度
    private int height;//屏幕高度
    private int dpi;//dpi

    private MediaRecorder mediaRecorder;
    private MediaRecordThread mediaRecordThread;

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

        requestPermission();

        start = findViewById(R.id.button);
        pause = findViewById(R.id.button2);

        start.setOnClickListener(v -> {
            //获取MediaProjectionManager ，通过这个类 申请权限，
            // 录屏是一个危险的权限，所以每次录屏的时候都得这么申请，用户同意了才行
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            //开启activity 然后在onActivityResult回调里面判断 用户是否同意录屏权限
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUSET_VIDEO);
        });
        pause.setOnClickListener(v -> {
         /*   if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }*/

            mediaRecordThread.release();
            mediaRecordThread = null;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUSET_AUDIO) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PERMISSION_GRANTED) {
                    Toast.makeText(this, "获得权限" + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUSET_VIDEO && resultCode == RESULT_OK) {//已经成功获取到权限
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
        MediaRecordThread mMediaRecordThread = mediaRecordThread = new MediaRecordThread(720, 1080, 8 * 1024 * 600, dpi, projection, filePath);
        mMediaRecordThread.start();
    }

    //录制视频 放在子线程最好，所以卸载
    class MediaRecordThread extends Thread {
        private int mWidth;//录制视频的宽
        private int mHeight;//录制视频的高
        private int mBitRate;//比特率 bits per second  这个经过我测试 并不是 一定能达到这个值
        private int mDpi;//视频的DPI
        private String mDstPath;//录制视频文件存放地点
        private MediaRecorder mMediaRecorder;//通过这个类录制
        private MediaProjection mediaProjection;//通过这个类 生成虚拟屏幕
        private final int FRAME_RATE = 60;//视频帧数 一秒多少张画面 并不一定能达到这个值
        private VirtualDisplay virtualDisplay;

        MediaRecordThread(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath) {
            mWidth = width;
            mHeight = height;
            mBitRate = bitrate;
            mDpi = dpi;
            this.mediaProjection = mediaProjection;
            mDstPath = dstPath;
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
                mMediaRecorder.start();
                Zprint.log(this.getClass(), "录屏线程内部开始工作");
            } catch (IllegalStateException | IOException e) {
//                e.printStackTrace();
                Zprint.log(this.getClass(), " 异常  ", e.toString());
            }
        }

        //实例化MediaRecordor
        void initMediaRecorder() throws IOException {
            mMediaRecorder = mediaRecorder = new MediaRecorder();
            //设置视频来源  录屏嘛 肯定是使用一个Surface作为视频源，如果录制视频的话 就是使用摄像头作为来源了 CAMERA
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            //设置要用于录制的音频源,必须在setOutputFormat()之前调用。参数有很多，英文注释也很简单，下面这个是录制麦克风，也就是外放的
            //记住 这个必要获得录制视频权限才行，要不然 报错
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //设置录制期间生成的输出文件的格式。必须在prepare()之前调用
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //录制文件存放位置
            mediaRecorder.setOutputFile(mDstPath);
            //录制视频的宽高
            mediaRecorder.setVideoSize(mWidth, mHeight);
            //FPS
            mediaRecorder.setVideoFrameRate(FRAME_RATE);
            //比特率
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
            if (mediaRecorder != null) {
                mediaRecorder.setOnErrorListener(null);
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;//help GC
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;//help GC
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;//help GC
            }
        }
    }


    /**
     * 录制视频 通过MediaCodec 和MediaMuxer ，这个可以获取视频流，也就是说可以直播
     *
     * @param isAsyn 是否异步录制，true 异步，false 同步
     */
    void startRecorderWithMediaCodec(boolean isAsyn) {

    }

    //MediaCodec  同步方式录制
    class ScreenRecorder extends Thread {
        private MediaProjection mProjection;
        private MediaCodec mediaCodec;
        private Surface mSurface;//mediaCodec 生成的输入surface  当做虚拟屏幕的输出surface
        private MediaMuxer mMediaMuxer;//通过这个将视频流写入本地文件，如果直播的话 不需要这个
        private VirtualDisplay mVirtualDisplay;
        private MediaCodec.BufferInfo bufferInfo;//缓冲区信息
        private int mVideoTrackIndex; //标识 mediaformat的 video信道
        private int mAudioTrackIndex;//音频信道
        private AtomicBoolean atomicBoolean;//控制是否退出录制

        private int mWidth;//录制视频的宽
        private int mHeight;//录制视频的高
        private int mBitRate;//比特率 bits per second  这个经过我测试 并不是 一定能达到这个值
        private int mDpi;//视频的DPI
        private String mDstPath;//录制视频文件存放地点
        private final int FRAME_RATE = 60;//视频帧数 一秒多少张画面 并不一定能达到这个值

        private AudioRecord mAudiorecord;
        // 音频源：音频输入-麦克风
        private final static int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
        // 采样率
        // 44100是目前的标准，但是某些设备仍然支持22050，16000，11025
        // 采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
        private final static int AUDIO_SAMPLE_RATE = 16000;
        // 音频通道 单声道
        private final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
        // 音频格式：PCM编码
        private final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        // 缓冲区大小：缓冲区字节大小
        private int audioBufferSize = 0;

        ScreenRecorder(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath) {
            mWidth = width;
            mHeight = height;
            mBitRate = bitrate;
            mDpi = dpi;
            this.mProjection = mediaProjection;
            mDstPath = dstPath;
            this.bufferInfo = new MediaCodec.BufferInfo();
        }

        @Override
        public void run() {
            super.run();
            try {
                //实例 编码器
                initEncoder();
                //实例 写入文件的类
                initMediaMuxer();
                mVirtualDisplay = mProjection.createVirtualDisplay("synchronous", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        //准备好编码器 得到编码器的surface 这样就不需要 我们对输入缓冲区 进行写数据了
        private void initEncoder() throws IOException {
            //这里的宽度，高度 是从左上角原点 开始算， 如果 没有大于 virtualDisplay的宽高，只会录制 一部分内容
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            //关键帧间隔 ，每隔一秒 截取一个完整的画面
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            //根据类型来生成 编码器
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            //配置
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mediaCodec.createInputSurface();//需要在createEncoderByType之后和start()之前才能创建，源码注释写的很清楚
            mediaCodec.start();
        }

        //如果 要录制mp4文件的话，需要调用这个方法 创建 MediaMuxer
        private void initMediaMuxer() throws Exception {
            //注意格式
            String filePath = filePath("同步mediaCodec.mp4");
            mMediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        private void recordVirtualDisplay() {
            while (atomicBoolean.get()) {
                //获取输出缓冲区的索引  同时 还会把缓冲区的信息 写入bufferInfo里面
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, -1);
                //大于等于0 就说明取到数据了
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


                    //录制mp4
//                    encodeToVideoTrack(outputBuffer);
                    //释放输出缓冲区的数据 这样才能接受新的数据
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //输出格式变化
                    MediaFormat newOutFormat = mediaCodec.getOutputFormat();
                    //H.264的
                    ByteBuffer sps = newOutFormat.getByteBuffer("csd-0");    // SPS
                    ByteBuffer pps = newOutFormat.getByteBuffer("csd-1");    // PPS
                    Zprint.log(this.getClass(), " 输出格式 有变化 ");
                    if (mMediaMuxer != null) {
                        //跟踪新的 格式的 信道
                        mVideoTrackIndex = mMediaMuxer.addTrack(newOutFormat);
                        mMediaMuxer.start();
                    }
                }
            }

            //输入流结束的信号 告诉编码器 结束流
            //这只能在编码器从{@link #createInputSurface创建的接受surface输入时使用
            mediaCodec.signalEndOfInputStream();
            mediaCodec.stop();
            //释放
            mediaCodec.release();
            mediaCodec = null;

            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            if (mMediaMuxer != null) {
                mMediaMuxer.stop();
                mMediaMuxer.release();
                mMediaMuxer = null;
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
            }
            if (output != null) {
                output.position(bufferInfo.offset);
                output.limit(bufferInfo.offset + bufferInfo.size);
                mMediaMuxer.writeSampleData(mVideoTrackIndex, output, bufferInfo);//写入mp4
            }
        }

        //录音的类
        void initAudioRecord() {
            audioBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING);
            mAudiorecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING, audioBufferSize);
        }
    }

    /**
     * @param fileName 文件名字
     * @return 文件的地址，默认在recorder文件夹下
     */
    public String filePath(String fileName) {
        String state = Environment.getExternalStorageState();
        String filePath;
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorder/" + fileName;
        } else {
            filePath = MyApplication.getApplication().getCacheDir().getAbsolutePath() + "/recorder/" + fileName;
        }
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdir();
        }
        return filePath;
    }

    /**
     * 请求录音权限
     */
    void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PERMISSION_DENIED) {
            String[] permission = {Manifest.permission.RECORD_AUDIO};
            requestPermissions(permission, REQUSET_AUDIO);
        }
    }


}
