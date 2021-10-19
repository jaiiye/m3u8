package xyz.jaiiye.video.m3u8;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import xyz.jaiiye.video.m3u8.spider.LutubeSpider;

/**
 * @author liyaling
 * @author jaiiye
 * @email ts_liyaling@qq.com
 * @date 2019/12/14 16:02
 */
public class M3u8Main {

    /**
     *
     * 解决java不支持AES/CBC/PKCS7Padding模式解密
     *
     */
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) {
        LutubeSpider lutubeSpider = new LutubeSpider(Constant.LUTUBE_LOGIN_CODE);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            lutubeSpider.saveCompletedVideos();
        }));
        lutubeSpider.start();
    }

}
