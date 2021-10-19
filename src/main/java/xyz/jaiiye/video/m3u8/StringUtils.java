package xyz.jaiiye.video.m3u8;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author liyaling
 * @author jaiiye
 * @email ts_liyaling@qq.com
 * @date 2019/12/14 16:27
 */
public final class StringUtils {

    public static boolean isBlank(String str) {
        return str == null || str.length() == 0;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().length() == 0;
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isUrl(String str) {
        if (isEmpty(str)) {
            return false;
        }
        return str.trim().matches("^(http|https)://.+");
    }

    public static String humanReadableByteCount(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int digit = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format("%.1f %sB", (float) bytes / (1l << (digit * 10)), " KMGTPE".charAt(digit));
    }

    public static byte[] decodeHex(String hex) {
        String[] list = hex.split("(?<=\\G.{2})");
        ByteBuffer buffer = ByteBuffer.allocate(list.length);
        for (String str : list) {
            buffer.put(Integer.valueOf(str, 16).byteValue());
        }
        return buffer.array();
    }

    public static String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            return url;
        }
    }

}
