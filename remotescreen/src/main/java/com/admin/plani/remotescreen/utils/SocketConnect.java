package com.admin.plani.remotescreen.utils;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class SocketConnect  implements Callable<Socket> {
    private Socket socket;
    private int port;
    private String host;
    private String TAG="SocketConnect ";
    public SocketConnect( String host,  int port) {
        this.port = port;
        this.host=host;
    }

    @Override
    public Socket call() throws Exception {
        Process process = Runtime.getRuntime().exec("ping -w 2" + " " + host);//判断是否连接的IP;
        int status = process.waitFor();
        Log.d(TAG, "IP地址是否ping通" + status);
        if (status == 0) {
            try {
                if (socket!=null){
                    if (socket.isConnected()){
                        socket.close();
                    }
                    //回收原本的内存空间
                    socket=null;
                }
                socket = new Socket();
                socket.setKeepAlive(true);
//                socket.setSendBufferSize();
                SocketAddress socketAddress = new InetSocketAddress(host, port);
                socket.connect(socketAddress, 3000);
                Log.d(TAG, "是否连接成功" + socket.isConnected());
                if (socket.isConnected()) {
                    return socket;
                }
            } catch (IOException e) {
                Log.d(TAG,  "  Socket端口被占用 建议重启pos端");
                e.printStackTrace();
            }
        }
        return null;
    }
}
