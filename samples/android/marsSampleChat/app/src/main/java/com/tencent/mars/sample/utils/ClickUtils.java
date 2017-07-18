package com.tencent.mars.sample.utils;

/**
 * Created by Pipi on 2017/7/7.
 */

public class ClickUtils {
    private static long lastClickTime;
    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if ( 0 < timeD && timeD < 2000) {               //两次点击时间相隔小于2000ms,便不会触发事件
            return true;
        }
        lastClickTime = time;
        return false;
    }
}
