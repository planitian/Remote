package com.admin.plani.clientscreen;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private TextView ip;
    private SurfaceView show;
    private MediaCodec mediaCodec;
    private Surface surface;
    private MediaCodec.BufferInfo bufferInfo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ip = findViewById(R.id.showIp);
        show = findViewById(R.id.show);
        surface = show.getHolder().getSurface();
        initMediaCodec();

    }

    public void  initSocket(Surface surface){
        try {
            ServerSocket serverSocket = new ServerSocket(7777);
            Socket socket = serverSocket.accept();
            InputStream is = socket.getInputStream();
            BufferedInputStream bi = new BufferedInputStream(is);

            while (true){
                if (bi.available()>0){

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void  initMediaCodec(){
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1080,1920);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        bufferInfo = new MediaCodec.BufferInfo();
        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format,surface,null, 0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
