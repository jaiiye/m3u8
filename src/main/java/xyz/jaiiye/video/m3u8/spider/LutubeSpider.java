/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.spider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import xyz.jaiiye.video.m3u8.Constant;
import xyz.jaiiye.video.m3u8.Log;
import xyz.jaiiye.video.m3u8.M3u8Exception;
import xyz.jaiiye.video.m3u8.MD5Helper;
import xyz.jaiiye.video.m3u8.StringUtils;
import xyz.jaiiye.video.m3u8.ThreadUtils;
import xyz.jaiiye.video.m3u8.download.M3u8Downloader;
import xyz.jaiiye.video.m3u8.net.CustomConnectException;
import xyz.jaiiye.video.m3u8.net.HttpURLConnectionHandler;
import xyz.jaiiye.video.m3u8.net.HttpUtils;
import xyz.jaiiye.video.m3u8.net.RetryHttpClient;

/**
 * Lutube网站视频爬虫
 *
 * @author jaiiye
 */
public class LutubeSpider {

    class SiteConfig implements Serializable {

        protected static final String CONFIG_URL = "https://lulutv.xyz/pwa.txt";
        protected static final String SPEED_TEST_URL_TEMPLATE = "%s/speed.html";
        protected static final String APP_INFO_COOKIE_URL_TEMPLATE = "%s/v1/appinfo";
        protected static final String LOGIN_URL_TEMPLATE = "%s/v1/fastlogin";
        protected static final String USER_INFO_URL_TEMPLATE = "%s/v1/user/info?token=%s";
        protected static final String VIDEO_LIST_URL_TEMPLATE = "%s/v2/videos/menu/0?token=%s&video_type=long&order=time&page=%d";
        protected static final String VIDEO_INFO_URL_TEMPLATE = "%s/v1/video/info/%s?token=%s";
        protected static final String VIDEO_STREAM_URL_TEMPLATE = "%s%s?token=%s";

        protected static final String LOGIN_CODE_KEY = "X-AFDAC809-9AD768A3";

        protected static final String AES_KEY = "322b63a3be0567ae7cae7a2f368ee38a";

        private final String api;
        private final String img;
        private final String stream;

        public SiteConfig(String api, String img, String stream) {
            super();
            this.api = api;
            this.img = img;
            this.stream = stream;
        }

        public String getApi() {
            return api;
        }

        public String getImg() {
            return img;
        }

        public String getStream() {
            return stream;
        }

    }

    class ConnectionResponseTimeHandler implements HttpURLConnectionHandler<Long> {

        private final long startTime;

        public ConnectionResponseTimeHandler() {
            super();
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public Long handle(HttpURLConnection httpURLConnection) throws IOException {
            final long endTime = System.currentTimeMillis();
            return httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK ? Long.MAX_VALUE
                    : endTime - startTime;
        }

    }

    private final String loginCode;

    private final RetryHttpClient<String> stringHttpClient;

    private final Set<String> finishedVideoIds;

    private transient String token;
    private transient UserInfo userInfo;

    //自动重新抓取数据重试次数
    private volatile int retryCount;
    //当前重试次数
    private volatile int reconnectionTimes;
    //当前抓取第几页数据
    private volatile int page;

    public LutubeSpider(String loginCode) {
        super();
        this.loginCode = loginCode;
        this.stringHttpClient = initDecryptedStringHttpClient();
        this.finishedVideoIds = initFinishedVideos();
        this.retryCount = 3;
        this.reconnectionTimes = 0;
        this.page = 1;
    }

    private RetryHttpClient<String> initDecryptedStringHttpClient() {
        RetryHttpClient retryHttpClient = new RetryHttpClient((httpURLConnection) -> {
            String app = httpURLConnection.getHeaderField("x-app-name");
            String content = (String) httpURLConnection.getContent();
            if (StringUtils.isEmpty(app)) {
                return content;
            }
            String vtag = httpURLConnection.getHeaderField("x-vtag");
            return decrypt(content, SiteConfig.AES_KEY, vtag);
        });
        return retryHttpClient;
    }

    /**
     * AES/CBC/PKCS7Padding解密
     *
     * @param content
     * @param aesKey
     * @param vtag
     * @return
     */
    private String decrypt(String content, String aesKey, String vtag) {
        try {
            String iv = MD5Helper.encrypt16(vtag);
            SecretKeySpec secretKeySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] decoded = Base64.getDecoder().decode(content);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException ex) {
            throw new IllegalStateException("解密失败！", ex);
        }
    }

    private Set<String> initFinishedVideos() {
        final Set<String> finishedVideos = new HashSet<>();
        initIgnoredVideos(finishedVideos);
        initCompletedVideos(finishedVideos);
        return finishedVideos;
    }

    protected void initIgnoredVideos(Set<String> videos) {
        Log.i("加载忽略视频列表数据...");
        String pathname = getClass().getResource("/video.ignore").getFile();
        List<String> lines = readAllLines(new File(pathname));
        videos.addAll(lines);
    }

    private List<String> readAllLines(File file) {
        final List<String> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String str;
            while ((str = br.readLine()) != null) {
                result.add(str);
            }
        } catch (IOException ex) {
            System.err.println("加载相关文件出错！" + ex.getMessage());
        }
        return result;
    }

    protected void initCompletedVideos(Set<String> videos) {
        Log.i("加载完成视频列表数据...");
        List<String> lines = readAllLines(getCompletedVideosFile());
        videos.addAll(lines);
    }

    private File getCompletedVideosFile() {
        File parent = getDefaultWorkingDirectory();
        if (!parent.exists()) {
            parent.mkdir();
        }
        return new File(parent, "video.completed");
    }

    public void saveCompletedVideos() {
        Log.i("保存完成视频列表数据...");
        File file = getCompletedVideosFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (String finishedVideoId : getFinishedVideoIds()) {
                bw.write(finishedVideoId);
                bw.newLine();
            }
            bw.flush();
        } catch (IOException ex) {
            System.err.println("写入数据（到相关文件）出错！" + ex.getMessage());
        }
    }

    protected File getDefaultWorkingDirectory() {
        final File file = new File(Constant.USER_HOME_DIR, "lutube");
        return file;
    }

    protected File getDefaultTmpDirectory() {
        final File file = new File(Constant.TMP_DIR, "lutube");
        return file;
    }

    public String getLoginCode() {
        return loginCode;
    }

    public RetryHttpClient<String> getStringHttpClient() {
        return stringHttpClient;
    }

    public Set<String> getFinishedVideoIds() {
        return finishedVideoIds;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 开始抓取数据
     */
    public void start() {
        Log.i("开始抓取Lutube数据...");
        SiteConfig siteConfig = getSiteConfig();
        getAppInfo(siteConfig);
        setupToken(siteConfig);
        setupUserInfo(siteConfig);
        try {
            List<String> videoIds;
            do {
                videoIds = grabVideos(siteConfig, page++);
                ThreadUtils.sleep(1000l);
            } while (!videoIds.isEmpty());
        } catch (CustomConnectException e) {
            System.err.println("获取视频列表或详情出错！" + e.getMessage());
            //获取视频列表或详情出错（超时）且可以重试时重新开始抓取
            if (++reconnectionTimes <= retryCount) {
                HttpUtils.waitForReconnection(reconnectionTimes);
                start(--page);
            }
        }
    }

    /**
     * 开始抓取第N页数据
     *
     * @param page
     */
    public void start(int page) {
        setPage(page);
        start();
    }

    /**
     * 获取网站相关配置（现在主要是url地址）
     *
     * @return
     */
    protected SiteConfig getSiteConfig() {
        Log.i("获取网站配置（Url）...");
        String text = stringHttpClient.get(SiteConfig.CONFIG_URL);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            Map<String, String> urls = jsonReader.readObject()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> entry.getKey(), entry -> handleMapValue(entry)));
            return new SiteConfig(urls.get("api"), urls.get("img"), urls.get("stream"));
        }
    }

    private String handleMapValue(Map.Entry<String, JsonValue> entry) {
        final JsonArray items = entry.getValue().asJsonArray();
        Map<String, Long> urlSpeeds = items.stream()
                .collect(Collectors.toMap(item -> item.asJsonObject().getString("url"), item -> speedTest(item)));
        return Collections.min(urlSpeeds.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private Long speedTest(JsonValue item) {
        String baseUrl = item.asJsonObject().getString("url");
        String url = String.format(SiteConfig.SPEED_TEST_URL_TEMPLATE, baseUrl);
        RetryHttpClient<Long> retryHttpClient = new RetryHttpClient(new ConnectionResponseTimeHandler(), 0);
        try {
            return retryHttpClient.get(url);
        } catch (CustomConnectException ex) {
            return Long.MAX_VALUE;
        }
    }

    protected void getAppInfo(SiteConfig siteConfig) {
        Log.i("获取应用信息（设置Cookie）...");
        final String url = String.format(SiteConfig.APP_INFO_COOKIE_URL_TEMPLATE, siteConfig.getApi());
        String text = stringHttpClient.get(url);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            JsonObject rootJsonObject = jsonReader.readObject();
            if (!rootJsonObject.containsKey("ad")) {
                throw new M3u8Exception("获取应用信息（设置Cookie）失败！");
            }
        }
    }

    protected void setupToken(SiteConfig siteConfig) {
        Log.i("获取并设置Token...");
        final String url = String.format(SiteConfig.LOGIN_URL_TEMPLATE, siteConfig.getApi());
        Map<String, Object> headers = new HashMap<String, Object>() {
            {
                put(SiteConfig.LOGIN_CODE_KEY, getLoginCode());
            }
        };
        String text = stringHttpClient.get(url, headers);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            JsonObject rootJsonObject = jsonReader.readObject();
            if (rootJsonObject.getJsonObject("status").getInt("code") == 200) {
                JsonObject responseJsonObject = rootJsonObject.getJsonObject("response");
                setToken(responseJsonObject.getString("token"));
                return;
            }
            throw new M3u8Exception("获取token失败！");
        }
    }

    protected void setupUserInfo(SiteConfig siteConfig) {
        Log.i("获取并设置用户信息...");
        final String url = String.format(SiteConfig.USER_INFO_URL_TEMPLATE, siteConfig.getApi(), getToken());
        String text = stringHttpClient.post(url);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            JsonObject rootJsonObject = jsonReader.readObject();
            if (rootJsonObject.getJsonObject("status").getInt("code") == 200) {
                JsonObject responseJsonObject = rootJsonObject.getJsonObject("response");
                UserInfo userInfo = new UserInfo();
                userInfo.setUserId(responseJsonObject.getJsonNumber("user_id").longValue());
                userInfo.setVip(responseJsonObject.getBoolean("vip"));
                userInfo.setInviteCode(responseJsonObject.getString("invite_code"));
                setUserInfo(userInfo);
                return;
            }
            throw new M3u8Exception("获取用户信息失败！");
        }
    }

    /**
     * 抓取视频
     *
     * @param siteConfig
     * @param page
     * @return
     */
    protected List<String> grabVideos(SiteConfig siteConfig, Integer page) {
        Log.i("获取视频列表（" + page + "）...");
        final String url = String.format(SiteConfig.VIDEO_LIST_URL_TEMPLATE, siteConfig.getApi(), getToken(), page);
        String text = stringHttpClient.get(url);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            JsonObject rootJsonObject = jsonReader.readObject();
            if (rootJsonObject.getJsonObject("status").getInt("code") == 200) {
                JsonObject responseJsonObject = rootJsonObject.getJsonObject("response");
                List<String> videoIds = responseJsonObject.getJsonArray("videos")
                        .stream()
                        .filter(item -> {
                            return getUserInfo().isVip() || item.asJsonObject().getJsonArray("main_tag").isEmpty();
                        })
                        .map(item -> item.asJsonObject().getString("video_id"))
                        .collect(Collectors.toList());
                videoIds.forEach((id) -> {
                    grabVideo(id, siteConfig);
                    ThreadUtils.sleep(1000l);
                });
                return videoIds;
            }
            throw new M3u8Exception("请求列表数据出错！");
        }
    }

    /**
     * 抓取某个视频
     *
     * @param id
     * @param siteConfig
     */
    protected void grabVideo(String id, SiteConfig siteConfig) {
        Log.i("抓取视频（" + id + "）...");
        if (!finishedVideoIds.contains(id)) {
            final VideoInfo videoInfo = getVideoInfo(id, siteConfig);
            String videoUrl = videoInfo.getVideoUrl();
            if (StringUtils.isNotBlank(videoUrl)) {
                File storageBaseDir = StringUtils.isNotEmpty(Constant.CUSTOM_STORAGE_BASE_DIR)
                        ? new File(Constant.CUSTOM_STORAGE_BASE_DIR, id)
                        : new File(getDefaultWorkingDirectory(), id);
                String filename = String.format("%s%s", videoInfo.getVideoNumber(), Constant.MP4_SUFFIX);
                File expectedFile = new File(storageBaseDir, filename);
                if (!expectedFile.exists()) {
                    String m3u8Url = String.format(SiteConfig.VIDEO_STREAM_URL_TEMPLATE, siteConfig.getStream(), videoUrl, getToken());
                    M3u8Downloader m3u8Downloader = new M3u8Downloader(m3u8Url);
                    File tmpDir = new File(getDefaultTmpDirectory(), id);
                    m3u8Downloader.setDownloadBaseDir(tmpDir.getPath());
                    m3u8Downloader.setFilename(filename);
                    m3u8Downloader.setMerged(true);
                    m3u8Downloader.setStorageBaseDir(storageBaseDir.getPath());
                    m3u8Downloader.start();
                }
                finishedVideoIds.add(id);
            }
        }
    }

    /**
     * 获取（请求）视频详细信息
     *
     * @param id
     * @param siteConfig
     * @return
     */
    private VideoInfo getVideoInfo(String id, SiteConfig siteConfig) {
        final String url = String.format(SiteConfig.VIDEO_INFO_URL_TEMPLATE, siteConfig.getApi(), id, getToken());
        String text = stringHttpClient.get(url);
        try (JsonReader jsonReader = Json.createReader(new StringReader(text))) {
            JsonObject rootJsonObject = jsonReader.readObject();
            if (rootJsonObject.getJsonObject("status").getInt("code") == 200) {
                JsonObject responseJsonObject = rootJsonObject.getJsonObject("response");
                return parseVideoInfo(responseJsonObject);
            }
            throw new M3u8Exception("请求视频详细信息出错！");
        }
    }

    /**
     * 解析视频详细信息
     *
     * @param jsonObject
     * @return
     */
    private VideoInfo parseVideoInfo(JsonObject jsonObject) {
        final VideoInfo videoInfo = new VideoInfo();
        videoInfo.setVideoId(jsonObject.getString("video_id"));
        videoInfo.setVideoTitle(jsonObject.getString("video_title"));
        JsonValue videoUrls = jsonObject.get("video_urls");
        if (JsonValue.ValueType.OBJECT.equals(videoUrls.getValueType())) {
            videoUrls.asJsonObject()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().matches("\\d+"))
                    .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                    .findFirst()
                    .ifPresent(entry -> {
                        videoInfo.setVideoUrl(getStringFromJsonValue(entry.getValue()));
                    });
        } else if (JsonValue.ValueType.STRING.equals(videoUrls.getValueType())) {
            videoInfo.setVideoUrl(getStringFromJsonValue(videoUrls));
        }
        videoInfo.setThumb(jsonObject.getString("thumb"));
        videoInfo.setCover(jsonObject.getString("cover"));
        JsonValue actors = jsonObject.get("actor");
        if (JsonValue.ValueType.ARRAY.equals(actors.getValueType())) {
            videoInfo.setActors(actors.asJsonArray().getValuesAs((t) -> {
                return getStringFromJsonValue(t);
            }));
        } else {
            videoInfo.setActors(Arrays.asList(getStringFromJsonValue(actors)));
        }
        videoInfo.setVideoNumber(jsonObject.getString("video_number"));
        videoInfo.setVideoCategory(jsonObject.getJsonArray("video_category").getValuesAs((t) -> {
            return getStringFromJsonValue(t);
        }));
        videoInfo.setVideoDuration(jsonObject.getInt("video_duration"));
        videoInfo.setVideoDescription(getStringFromJsonValue(jsonObject.get("video_description")));
        videoInfo.setVideoTags(jsonObject.getJsonArray("video_tags").getValuesAs((t) -> {
            return getStringFromJsonValue(t);
        }));
        videoInfo.setReleaseDate(new Date(jsonObject.getInt("release_date")));
        videoInfo.setUploadDate(new Date(jsonObject.getInt("upload_date")));
        return videoInfo;
    }

    protected String getStringFromJsonValue(JsonValue jsonValue) {
        if (jsonValue instanceof JsonString) {
            JsonString jsonString = (JsonString) jsonValue;
            return jsonString.getString();
        }
        return null;
    }

}
