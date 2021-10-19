/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.net;

import xyz.jaiiye.video.m3u8.ThreadUtils;

/**
 *
 * @author jaiiye
 */
public final class HttpUtils {

    protected static final String DEFAULT_ENCODING = "UTF-8";

    protected static final long ONE_MINUTE_MILLIS = 1l * 60 * 1000;

    public static String getEncodingFromContentType(String contentType) {
        String[] headers = contentType.split(";");
        for (String header : headers) {
            String[] params = header.split("=");
            if (params.length == 2) {
                if (params[0].equalsIgnoreCase("charset")) {
                    return params[1];
                }
            }
        }
        return DEFAULT_ENCODING;
    }

    public static void waitForReconnection(int reconnectionTimes) {
        //重试链接间隔（时间）
        long retryInterval = 1000l * (1 << (reconnectionTimes - 1));
        if (retryInterval > ONE_MINUTE_MILLIS) {
            retryInterval = ONE_MINUTE_MILLIS;
        }
        System.out.println(retryInterval + "ms后尝试第" + reconnectionTimes + "次重试");
        ThreadUtils.sleep(retryInterval);
    }

}
