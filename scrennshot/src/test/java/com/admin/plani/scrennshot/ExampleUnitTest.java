package com.admin.plani.scrennshot;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        CharBuffer charBuffer = CharBuffer.allocate(10);
        String content = "asdsfsafwrgdfgretfgfdhgdfh";
        char[] charsContent = content.toCharArray();
        for (int i = 0; i <charBuffer.limit() ; i++) {
            charBuffer.put(charsContent[i]);
        }

        char[] chars = new char[charBuffer.limit()];
        charBuffer.flip();
        charBuffer.get(chars);
        for (int i = 0; i <chars.length ; i++) {
            System.out.println(chars[i]);
        }
    }
}