package xyz.jaiiye.video.m3u8;

/**
 * 常量
 *
 * @author liyaling
 * @author jaiiye
 * @email ts_liyaling@qq.com
 * @date 2019/12/23 10:11
 */
public class Constant {

    //文件分隔符，在window中为\\，在linux中为/
    public static final String FILESEPARATOR = System.getProperty("file.separator");

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36 Edg/90.0.818.66";

    public static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    public static final String USER_HOME_DIR = System.getProperty("user.home");

    public static final String CUSTOM_STORAGE_BASE_DIR = System.getenv("CUSTOM_STORAGE_BASE_DIR");

    public static final int DEFAULT_RETRY_TIMES = 5;

    public static final int DEFAULT_THREAD_COUNT = 8;

    //默认文件每次读取字节数
    public static final int MEDIA_BYTE_COUNT = 8192;

    //日志级别 控制台不输出
    public static final int NONE = 0X453500;

    //日志级别 控制台输出所有信息
    public static final int INFO = 0X453501;

    //日志级别 控制台输出调试和错误信息
    public static final int DEBUG = 0X453502;

    //日志级别 控制台只输出错误信息
    public static final int ERROR = 0X453503;

    public static final String MP4_SUFFIX = ".mp4";

    public static final String M3U8_SUFFIX = ".m3u8";

    public static final String HEX_PREFIX = "0x";

    public static final String LUTUBE_LOGIN_CODE = "bdEH5jnTfBaJh1++S8UpfL30zqCbATr1zG6IZlbirzi7yJ2m";

}
