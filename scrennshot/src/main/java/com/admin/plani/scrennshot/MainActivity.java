package com.admin.plani.scrennshot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.admin.plani.scrennshot.threadtes.SocketConnect;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

public class MainActivity extends AppCompatActivity {
    private TextView shot;
    private static final int SCREEN_SHOT = 1;
    private MediaProjection projection;
    MediaProjectionManager projectionManager;
    private AlertDialog alertDialog;
    private ImageReader imageReader;

    Bitmap bitmap;

    private Button button;

    private SurfaceView surfaceView;

    private int width;
    private int height;
    private int dpi;

    private AtomicBoolean atomicBoolean = new AtomicBoolean(true);

    private Socket socket;

    private WebSocket webSocket;


    private ImageView imageView;

    private MediaCodec mediaCodec;
    private Surface inputSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!isPermission()) {
            obtainPermission();
        }
        imageView = findViewById(R.id.imageView);
        shot = findViewById(R.id.screen_shot);
        button = findViewById(R.id.image_shot);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                atomicBoolean.set(false);
                Toast.makeText(MainActivity.this, " 录屏停止", Toast.LENGTH_LONG).show();
            }
        });
        surfaceView = findViewById(R.id.surface_shot);
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        width = point.x;
        height = point.y;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        dpi = metrics.densityDpi;
        System.out.println("width height dpi " + width + "    " + height + "    " + dpi);
        shot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_SHOT);

            }
        });
//        initSocket();
        new Thread(() -> initNvSocket()).start();
        Zprint.log(this.getClass(), "当前屏幕方向", getDgree());

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                Canvas canvas = holder.getSurface().lockCanvas(null);

                Paint paint = new Paint();
                canvas.drawColor(getResources().getColor(R.color.colorPrimary));
                paint.setColor(getResources().getColor(R.color.colorPrimary));

                canvas.drawLine(500, 500, 1000, 1000, paint);
                canvas.save(Canvas.ALL_SAVE_FLAG);
                holder.getSurface().unlockCanvasAndPost(canvas);
                View view = new View(MainActivity.this);
                view.setDrawingCacheEnabled(true);
                view.buildDrawingCache();
                Bitmap bitmap= view.getDrawingCache();
                if (imageView!=null){
                  runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                          imageView.setImageBitmap(bitmap);
                      }
                  });
              }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        System.out.println("requestCode   " + requestCode);
        System.out.println("resultCode   " + resultCode);

        switch (requestCode) {
            case SCREEN_SHOT:
                if (resultCode == RESULT_OK) {
                    projection = projectionManager.getMediaProjection(resultCode, data);
                    if (projection == null) {
                        System.out.println("projection  为空");
                        return;
                    }
                    String path = FileUtils.filePath("one.mp4");
                    File file = new File(path);
                    Zprint.log(this.getClass(), "录屏文件的位置", file.getAbsolutePath());

                    projection.createVirtualDisplay("image", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surfaceView.getHolder().getSurface(), null, null);
/*
                    MediaRecordThread mediaRecordThread = new MediaRecordThread(width, height, 6000000,
                            dpi, projection, file.getAbsolutePath());
                    mediaRecordThread.start();*/

                    ScreenRecorder screenRecorder = new ScreenRecorder(projection);
                    new Thread(screenRecorder).start();
                    this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "开始录屏", Toast.LENGTH_LONG).show());
                    break;


                    //>>>>>>>>>>>>>>>>>>>
           /*         imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 15);
                    projection.createVirtualDisplay("shot", width, height, dpi,  DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
                    SystemClock.sleep(1000);
                    Timer timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            Zprint.log(this.getClass()," 还可以取几张图片 ",imageReader.getMaxImages());
                            Image image = imageReader.acquireNextImage();
                            int width = image.getWidth();
                            int height = image.getHeight();
                            final Image.Plane[] planes = image.getPlanes();
                            final ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * width;
                            bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(buffer);
                            image.close();
                            reflect(imageReader);
                            imageView.post(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(bitmap);
                                    imageView.setBackgroundColor(getColor(android.R.color.black));
                                }
                            });
                        }
                    },1000,1000);*/
            /*        try {
                        prepereEncoder();
                        projection.createVirtualDisplay("image", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surfaceView.getHolder().getSurface(), null, null);
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                        while (true) {
                            int out = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                            if (out >= 0) {
                               *//* Image image = mediaCodec.getOutputImage(out);
                                int width = image.getWidth();
                                int height = image.getHeight();
                                final Image.Plane[] planes = image.getPlanes();
                                final ByteBuffer buffer = planes[0].getBuffer();
                                int pixelStride = planes[0].getPixelStride();
                                int rowStride = planes[0].getRowStride();
                                int rowPadding = rowStride - pixelStride * width;
                                bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                                bitmap.copyPixelsFromBuffer(buffer);
                                image.close();
                                imageView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        imageView.setImageBitmap(bitmap);
                                        imageView.setBackgroundColor(getColor(android.R.color.black));
                                    }
                                });*//*

                            } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat mediaFormat = mediaCodec.getOutputFormat();

                            }else if (out == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                }
                break;
        }

    }


    private void prepereEncoder() throws IOException {
        //这里的宽度，高度 是从左上角原点 开始算， 如果 没有大于 virtualDisplay的宽高，只会录制 一部分内容
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mediaCodec.configure(format, null, null, 0);
//        inputSurface = mediaCodec.createInputSurface();//需要在createEncoderByType之后和start()之前才能创建，源码注释写的很清楚
        mediaCodec.start();
    }



    public void reflect(ImageReader imageReader) {
        Class<?> cla = imageReader.getClass();
        try {
            Method discardFreeBuffers = cla.getDeclaredMethod("discardFreeBuffers");
            discardFreeBuffers.invoke(imageReader);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    class MediaRecordThread extends Thread {
        private int mWidth;
        private int mHeight;
        private int mBitRate;
        private int mDpi;
        private String mDstPath;
        private MediaRecorder mediaRecorder;
        private MediaProjection mediaProjection;
        private final int FRAME_RATE = 60;//fps

        private VirtualDisplay virtualDisplay;

        public MediaRecordThread(int width, int height, int bitrate, int dpi, MediaProjection mediaProjection, String dstPath) {
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
                initMediaRecordor();
                virtualDisplay = mediaProjection.createVirtualDisplay("luing", mWidth, mHeight, mDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mediaRecorder.getSurface(), null, null
                );
                mediaRecorder.start();
                Zprint.log(this.getClass(), "录屏线程内部开始工作");
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } finally {
//                release();
            }
        }

        void initMediaRecordor() {

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(mDstPath);
            mediaRecorder.setVideoSize(mWidth, mHeight);
            mediaRecorder.setVideoFrameRate(FRAME_RATE);
            mediaRecorder.setVideoEncodingBitRate(mBitRate);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void release() {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaRecorder != null) {
                mediaRecorder.setOnErrorListener(null);
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        }
    }


    public class ScreenRecorder implements Runnable {
        private MediaProjection mediaProjection;
        private MediaCodec mediaCodec;
        private Surface mSurface;
        private MediaMuxer mediaMuxer;
        private VirtualDisplay virtualDisplay;
        private MediaCodec.BufferInfo bufferInfo;
        private MediaFormat outputFormat;
        private int mVideoTrackIndex;

        public ScreenRecorder(MediaProjection mediaProjection) {
            this.mediaProjection = mediaProjection;
            this.bufferInfo = new MediaCodec.BufferInfo();

        }

        @Override
        public void run() {
            try {

                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "first.mp4";
                File out = new File(path);
                mediaMuxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                prepereEncoder();
                virtualDisplay = mediaProjection.createVirtualDisplay("luping", width, height,
                        dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null);
                Zprint.log(this.getClass(), "created virtual display:");
//                recordVirtualDisplay();

                long befor = System.currentTimeMillis();
                while (true){
                    if ((System.currentTimeMillis()-befor)>(3*1000)){
                        Zprint.log(this.getClass()," 跳出循环？》》》》》》》》》》》》》》");
                        break;
                    }
                }
                mediaCodec.signalEndOfInputStream();
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;

                release();

                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void prepereEncoder() throws IOException {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    Zprint.log(this.getClass(),"运行");
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                        encodeToVideoTrack(index,codec);
                        codec.releaseOutputBuffer(index, false);
                        Zprint.log(this.getClass(),"运行");
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    System.out.println("异常   "+e.toString());
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Zprint.log(this.getClass(),"运行");
//                    MediaFormat newOutFormat = codec.getOutputFormat();
                    mVideoTrackIndex = mediaMuxer.addTrack(format);
                    mediaMuxer.start();
                }
            });
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mSurface = mediaCodec.createInputSurface();//需要在createEncoderByType之后和start()之前才能创建，源码注释写的很清楚
            mediaCodec.start();
        }

        private void recordVirtualDisplay() {
            while (atomicBoolean.get()) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    MediaFormat bufferFormat = mediaCodec.getOutputFormat(outputBufferId);
//                    outputBuffer.flip();
                   /* byte[] bytes = new byte[outputBuffer.limit()];
                    outputBuffer.get(bytes);
                    System.out.println(">>>>>>>>> " + bytes.length);*/
                    /*      webSocket.sendBinary(bytes);*/
                    encodeToVideoTrack(outputBufferId);
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newOutFormat = mediaCodec.getOutputFormat();
                 /*   ByteBuffer spsBuffer = newOutFormat.getByteBuffer("csd-0");    // SPS
                    ByteBuffer ppsBuffer = newOutFormat.getByteBuffer("csd-1");    // PPS
                    byte[] sps = new byte[spsBuffer.limit()];
                    spsBuffer.get(sps);
                    byte[] pps = new byte[ppsBuffer.limit()];
                    ppsBuffer.get(pps);*/
                 /*   webSocket.sendBinary(sps);
                    webSocket.sendBinary(pps);*/
                    mVideoTrackIndex = mediaMuxer.addTrack(newOutFormat);
                    mediaMuxer.start();
                } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {

                }
            }
            mediaCodec.signalEndOfInputStream();
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;

            release();

            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }

        private void encodeToVideoTrack(int index) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.
                // Ignore it.
                bufferInfo.size = 0;
            }
            if (bufferInfo.size == 0) {
                encodedData = null;
            } else {
            }
            if (encodedData != null) {
                encodedData.position(bufferInfo.offset);
                encodedData.limit(bufferInfo.offset + bufferInfo.size);
                mediaMuxer.writeSampleData(mVideoTrackIndex, encodedData, bufferInfo);//写入
            }
        }

        private void encodeToVideoTrack(int index,MediaCodec mediaCodec) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(index);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.
                // Ignore it.
                bufferInfo.size = 0;
            }
            if (bufferInfo.size == 0) {
                encodedData = null;
            }
            if (encodedData != null) {
                encodedData.position(bufferInfo.offset);
                encodedData.limit(bufferInfo.offset + bufferInfo.size);
                mediaMuxer.writeSampleData(mVideoTrackIndex, encodedData, bufferInfo);//写入
            }
        }

     /*   public boolean getInputBuffer(ByteBuffer inputBuffer, MediaCodec mediaCodec, int index) {
            if (index >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                inputBuffer = outputBuffer;
            }


        }*/

        private void resetOutputFormat() {
            MediaFormat newFormat = mediaCodec.getOutputFormat();
            // 在此也可以进行sps与pps的获取，获取方式参见方法getSpsPpsByteBuffer()
            Zprint.log(this.getClass(), "output format changed.\\n new format", newFormat.toString());
            mVideoTrackIndex = mediaMuxer.addTrack(newFormat);
            mediaMuxer.start();
        }


        void release() {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        }
    }


    //获取屏幕方向
    private int getDgree() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }

    public void initSocket() {
        SocketConnect socketConnect = new SocketConnect("192.168.1.149", 8080);
        FutureTask<Socket> futureTask = new FutureTask<Socket>(socketConnect);
        new Thread(futureTask).start();
        try {
            Socket inner = futureTask.get();
            if (inner != null) {
                socket = inner;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }


    public void sendData(byte[] data) {
        OutputStream outputStream = null;
        Zprint.log(this.getClass(), "发送的数据", data.toString());
        try {
            outputStream = socket.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
            dataOutputStream.write(data);
            dataOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initNvSocket() {
        try {
            webSocket = new WebSocketFactory().createSocket("ws://192.168.2.144:8025/remote/control", 1000)
                    .setFrameQueueSize(9)
                    .setMissingCloseFrameAllowed(false)
                    .addListener(new WsListener())
                    .connect();
        } catch (IOException | WebSocketException e) {
            e.printStackTrace();
        }
    }

    class WsListener extends WebSocketAdapter {
        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            super.onTextMessage(websocket, text);
            Zprint.log(this.getClass(), "webSocket得到消息",text);

        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            super.onConnected(websocket, headers);
            Zprint.log(this.getClass(), "webSocket已经连接");

            sendHeart(webSocket);

        }

        @Override
        public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
            super.onConnectError(websocket, exception);
            Zprint.log(this.getClass(), "webSocket出现错误", exception);
        }

        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
        }
    }

    private void sendHeart(WebSocket webSocket){


        String sn = SNandCodeUtil.getAndroidOsSystemProperties(SNandCodeUtil.SN);
        if (sn==null||sn.isEmpty()){
            sn = SNandCodeUtil.SERIALNUMBER;
        }
        Zprint.log(this.getClass(),"sn",sn);
        JSONObject heart = new JSONObject();
        try {
            heart.put("code", "1000");
            heart.put("eq_sn", sn);

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    webSocket.sendText(heart.toString());
                    Zprint.log(this.getClass(),"发送心跳");
                }
            };
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(task, 1, 5000);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //是否拥有权限
    public boolean isPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission
                .WRITE_EXTERNAL_STORAGE);
        return permission == PackageManager.PERMISSION_GRANTED;
    }

    //获取权限
    public void obtainPermission() {
        alertDialog = new AlertDialog.Builder(this).setTitle("权限申请").setMessage
                ("给app开启存储空间权限，注意你不开启，app将退出").setPositiveButton("确定", new DialogInterface
                .OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                requestPermission();
            }
        }).setNegativeButton("退出app", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
                System.exit(0);
            }
        }).show();
    }

    //请求权限
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission
                .WRITE_EXTERNAL_STORAGE}, 1);
    }
}
