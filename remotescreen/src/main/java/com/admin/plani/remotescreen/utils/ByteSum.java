package com.admin.plani.remotescreen.utils;

public class ByteSum {
    public static long Sum(byte[] src){
        long sum = 0;
        for (int i = 0; i <src.length ; i++) {
            sum += src[i];
        }
        return sum;
    }
}
