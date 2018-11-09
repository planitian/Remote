package com.admin.plani.remotescreen.utils;

/**
 * 创建时间 2018/11/8
 *
 * @author plani
 */
public class ByteUtils {
    public static byte[] IntToByteArray(int n) {
        //一个int 四个byte字节
        byte[] b = new byte[4];
        //  0xff 二进制的话是11111111，因为  n是整型32位 所以 n和0xff与运算时候 0xff会在高位补零 变成 00000.....11111111
        //
        b[0] = (byte) (n & 0xff);

        b[1] = (byte) (n >> 8 & 0xff);
        b[2] = (byte) (n >> 16 & 0xff);
        b[3] = (byte) (n >> 24 & 0xff);
        return b;
    }

    public static int ByteArrayToInt(byte[] bArr) {
        if (bArr.length != 4) {
            return -1;
        }
        return (int) ((((bArr[3] & 0xff) << 24)
                | ((bArr[2] & 0xff) << 16)
                | ((bArr[1] & 0xff) << 8)
                | ((bArr[0] & 0xff) << 0)));
    }
}
