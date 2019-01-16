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
import android.util.Log;
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
    //录制屏幕 加声音 同步方式
    class AudioRecorderThread extends Thread {
        private AudioRecord mAudiorecord;//录音类

        private MediaMuxer mMediaMuxer;//通过这个将视频流写入本地文件，如果直播的话 不需要这个

        // 音频源：音频输入-麦克风  我使用其他格式 就会报错
        private final static int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
        // 采样率
        // 44100是目前的标准，但是某些设备仍然支持22050，16000，11025
        // 采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
        private final static int AUDIO_SAMPLE_RATE = 44100;
        // 音频通道 默认的 可以是单声道 立体声道
        private final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_DEFAULT;
        // 音频格式：PCM编码   返回音频数据的格式
        private final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        //记录期间写入音频数据的缓冲区的总大小(以字节为单位)。
        private int audioBufferSize = 0;
        //缓冲数组 ，用来读取audioRecord的音频数据
        private byte[] byteBuffer;

        private int audioIndex;//通过MediaMuxer 向本地文件写入数据时候，这个标志是用来确定信道的
        private int videoIndex;//上同

        private MediaCodec mAudioMediaCodec;//音频编码器

        private MediaCodec mVideoMediaCodec;//视频编码器

        private MediaFormat audioFormat;//音频编码器 输出数据的格式
        private MediaFormat viedeoFormat;//视频编码器 输出数据的格式

        private MediaProjection mediaProjection;//通过这个类 生成虚拟屏幕
        private Surface surface;//视频编码器 生成的surface ，用于充当 视频编码器的输入源
        private VirtualDisplay virtualDisplay; //虚拟屏幕
        //这个是每次在编码器 取数据的时候，这个info 携带取出数据的信息，例如 时间，大小 类型之类的  关键帧 可以通过这里的flags辨别
        private MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        private MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();

        private volatile   boolean isRun = true;//用于控制 是否录制，这个无关紧要


        private int mWidth;//录制视频的宽
        private int mHeight;//录制视频的高
        private int mBitRate;//比特率 bits per second  这个经过我测试 并不是 一定能达到这个值
        private int mDpi;//视频的DPI
        private String mDstPath;//录制视频文件存放地点
        private final int FRAME_RATE = 60;//视频帧数 一秒多少张画面 并不一定能达到这个值

        public AudioRecorderThread(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath) {
            this.mediaProjection = mediaProjection;
            this.mWidth = width;
            this.mHeight = height;
            mBitRate = bitrate;
            mDpi = dpi;
            mDstPath = dstPath;
        }

        @Override
        public void run() {
            super.run();
            try {
                //实例化 AudioRecord
                initAudioRecord();
                //实例化 写入文件的类
                initMediaMuxer();
                //实例化 音频编码器
                initAudioMedicode();
                //实例化 视频编码器
                initVideoMedicodec();
                //开始
                mAudioMediaCodec.start();
                mVideoMediaCodec.start();
                int timeoutUs = -1;//这个主要是为了 第一次进入while循环 视频编码器 能阻塞到 有视频数据输出 才运行
                String TAG = "audio";
                while (isRun) {
                    //获取 输出缓冲区的索引 通过索引 可以去到缓冲区，缓冲区里面存着 编码后的视频数据 。 timeoutUs为负数的话，会一直阻塞到有缓冲区索引，0的话 立刻返回
                    int videoOutputID = mVideoMediaCodec.dequeueOutputBuffer(videoInfo, timeoutUs);
                    Log.d(TAG, "video flags " + videoInfo.flags);
                    timeoutUs = 0;//第二次 视频编码器 就不需要 阻塞了  0 立刻返回
                    //索引大于等于0 就代表有数据了
                    if (videoOutputID >= 0) {
                        Zprint.log(this.getClass(), "VIDEO 输出", videoOutputID, videoInfo.presentationTimeUs);
                        //flags是2的时候 代表输出的数据 是配置信息，不是媒体信息
                        if (videoInfo.flags != 2) {
                            //得到缓冲区
                            ByteBuffer outBuffer = mVideoMediaCodec.getOutputBuffer(videoOutputID);
                            outBuffer.flip();//准备读取
                            //写入文件中  注意 videoIndex
                            mMediaMuxer.writeSampleData(videoIndex, outBuffer, videoInfo);
                        }
                        //释放缓冲区，毕竟缓冲区一共就两个 一个输入 一个输出，用完是要还回去的
                        mVideoMediaCodec.releaseOutputBuffer(videoOutputID, false);
                    } else if (videoOutputID == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//输出格式有变化
                        Zprint.log(this.getClass(), "video Format 改变");
                        viedeoFormat = mVideoMediaCodec.getOutputFormat();//得到新的输出格式
                        videoIndex = mMediaMuxer.addTrack(viedeoFormat);//重新确定信道
                    }
                    //得到 输入缓冲区的索引
                    int audioInputID = mAudioMediaCodec.dequeueInputBuffer(0);
                    //也是大于等于0 代表 可以输入数据啦
                    if (audioInputID >= 0) {
                        Zprint.log(this.getClass(), "audio 输入", audioInputID);
                        ByteBuffer audioInputBuffer = mAudioMediaCodec.getInputBuffer(audioInputID);
                        audioInputBuffer.clear();
                        //从 audiorecord 里面 读取原始的音频数据
                        int read = mAudiorecord.read(byteBuffer, 0, audioBufferSize);
                        if (read < audioBufferSize) {
                            System.out.println(" 读取的数据" + read);
                        }
                        //上面read可能小于audioBufferSize  要注意
                        audioInputBuffer.put(byteBuffer, 0, read);
                        //入列  注意下面的时间，这个是确定这段数据 时间的 ，视频音频 都是一段段的数据，每个数据都有时间 ，这样播放器才知道 先播放那个数据
                        // 串联起来 就是连续的了
                        mAudioMediaCodec.queueInputBuffer(audioInputID, 0, read, System.nanoTime()/1000L, 0);
                    }
                    //音频输出
                    int audioOutputID = mAudioMediaCodec.dequeueOutputBuffer(audioInfo, 0);
                    Log.d(TAG, "audio flags " + audioInfo.flags);
                    if (audioOutputID >= 0) {
                        Zprint.log(this.getClass(), "audio 输出", audioOutputID, audioInfo.presentationTimeUs);
                        audioInfo.presentationTimeUs = videoInfo.presentationTimeUs;//保持 视频和音频的统一，防止 时间画面声音 不同步
                        if (audioInfo.flags != 2) {
                            ByteBuffer audioOutBuffer = mAudioMediaCodec.getOutputBuffer(audioOutputID);
                            audioOutBuffer.limit(audioInfo.offset + audioInfo.size);//这是另一种 和上面的 flip 没区别
                            audioOutBuffer.position(audioInfo.offset);
                            mMediaMuxer.writeSampleData(audioIndex, audioOutBuffer, audioInfo);//写入
                        }
                        //释放缓冲区
                        mAudioMediaCodec.releaseOutputBuffer(audioOutputID, false);
                    } else if (audioOutputID == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Zprint.log(this.getClass(), "audio Format 改变");
                        audioFormat = mAudioMediaCodec.getOutputFormat();
                        audioIndex = mMediaMuxer.addTrack(audioFormat);
                        //注意 这里  只在start  视频哪里没有这个，这个方法只能调用一次
                        mMediaMuxer.start();
                    }
                }
                //释放资源
                stopRecorder();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //实例化 AUDIO 的编码器
        void initAudioMedicode() throws IOException {
            audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
            //描述要使用的AAC配置文件的键(仅适用于AAC音频格式)。
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize << 1);//最大输入

            //这里注意  如果 你不确定 你要生成的编码器类型，就通过下面的 通过类型生成编码器
            mAudioMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            //配置
            mAudioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        //实例化 VIDEO 的编码器
        void initVideoMedicodec() throws IOException {
            //这里的width height 就是录制视频的分辨率，可以更改  如果这里的分辨率小于 虚拟屏幕的分辨率 ，你会发现 视频只录制了 屏幕部分内容
            viedeoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
            viedeoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            viedeoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);//比特率 bit单位
            viedeoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 60);//FPS
            viedeoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);//关键帧  是完整的一张图片，其他的都是部分图片
            //通过类型创建编码器  同理 创建解码器也是一样
            mVideoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            //配置
            mVideoMediaCodec.configure(viedeoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //让视频编码器 生成一个弱引用的surface， 这个surface不会保证视频编码器 不被回收，这样 编码视频的时候 就不需要 传输数据进去了
            surface = mVideoMediaCodec.createInputSurface();
            //创建虚拟屏幕，让虚拟屏幕内容 渲染在上面的surface上面 ，这样 才能 不用传输数据进去
            virtualDisplay = mediaProjection.createVirtualDisplay("video", mWidth, mHeight, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
        }

        //录音的类，用于给音频编码器 提供原始数据
        void initAudioRecord() {
            //得到 音频录制时候 最小的缓冲区大小
            audioBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING);
            byteBuffer = new byte[audioBufferSize];
            //两种方式 都可以
//            mAudiorecord = new AudioRecord(AUDIO_SOURCE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING, audioBufferSize);
            //通过builder方式创建
            mAudiorecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(32000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(audioBufferSize)
                    .build();
            //开始录制，这里可以检查一下状态，但只要代码无误，检查是无需的 state
            mAudiorecord.startRecording();
        }

        //如果 要录制mp4文件的话，需要调用这个方法 创建 MediaMuxer
        private void initMediaMuxer() throws Exception {
            //注意格式  创建录制的文件
            String filePath = filePath("luyi.mp4");
            //实例化 MediaMuxer 编码器取出的数据，通过它写入文件中
            mMediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        void stopRecorder() {
            mVideoMediaCodec.stop();
            mVideoMediaCodec.release();
            mVideoMediaCodec = null;

            mAudioMediaCodec.stop();
            mAudioMediaCodec.release();
            mAudioMediaCodec = null;


            mAudiorecord.stop();
            mAudiorecord.release();
            mAudiorecord = null;

            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;

            virtualDisplay.release();
            virtualDisplay = null;
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
