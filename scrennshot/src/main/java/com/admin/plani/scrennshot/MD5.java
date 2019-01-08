package com.admin.plani.scrennshot;

import java.security.MessageDigest;

public class MD5 {

	/**
	 * 加密算法MD5
	 * @param text 明文
	 * @return String 密文
	 * 此方法经过修改，如果用户传入的是 空或null 那么就返回空
	 */
	public final static String encoding(String text) {
		//自己的加入的
		if(text==null||text.equals("")){
			return "";
		}
		
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		String encodingStr = null;

		try {
			byte[] strTemp = text.getBytes();
			MessageDigest mdTemp = MessageDigest.getInstance("MD5");
			mdTemp.update(strTemp);
			byte[] md = mdTemp.digest();
			int j = md.length;
			char str[] = new char[j * 2];
			int k = 0;
			for (int i = 0; i < j; i++) {
				byte byte0 = md[i];
				str[k++] = hexDigits[byte0 >>> 4 & 0xf];
				str[k++] = hexDigits[byte0 & 0xf];
			}

			encodingStr = new String(str);
		} catch (Exception e) {
		}

		return encodingStr;
	}

}
