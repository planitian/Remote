package com.admin.plani.scrennshot;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 创建时间 2018/8/23
 *
 * @author plani
 */
public class SNandCodeUtil {
    //sign key
    private static final String SIGN_KEY = "wxsp";
    //sign_secret
    private static final String SIGN_SECRET = "438b51f71c1f50743373110d4d5c58ee";


    //固件编译时间
    public static final String FIRMGRADLE = "ro.build.date.utc";

    //系统的sn码
    public static final String SN = "ro.boot.serialno";

    //系统唯一码 如果没有通话功能的设备，会返回唯一码
    public static final String SERIALNUMBER = android.os.Build.SERIAL;

    /**
     * 反射获取定制SN码
     *
     * @param key
     * @return
     */
    public static String getAndroidOsSystemProperties(String key) {
        String ret;
        try {
            Method systemProperties_get = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class);
            if ((ret = (String) systemProperties_get.invoke(null, key)) != null)
                return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return "";
    }


    public static Map<String, String> getParameter() {

        String time = String.valueOf(System.currentTimeMillis());

        String sign = MD5.encoding(SIGN_SECRET + SIGN_KEY + SIGN_SECRET + time + SIGN_SECRET).toUpperCase();

        Map<String, String> result = new HashMap<>();
        result.put("timestamp", time);
        result.put("sign", sign);
        String eq_sn=getAndroidOsSystemProperties(SN);

        String eq_code=SERIALNUMBER;
        if (eq_code==null||eq_code.isEmpty()){
             eq_code=getAndroidOsSystemProperties(SN);

        }
        if (eq_sn==null||eq_sn.isEmpty()){
            eq_sn=eq_code;
        }
        result.put("eq_sn", eq_sn);
        result.put("eq_code",eq_code );
        Zprint.log(SNandCodeUtil.class, "参数", result.toString());
        return result;

    }
}
