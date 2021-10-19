/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.net;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import xyz.jaiiye.video.m3u8.Constant;
import xyz.jaiiye.video.m3u8.StringUtils;

/**
 *
 * @author jaiiye
 */
public final class HttpURLConnectionFactory {

    private static final HttpURLConnectionFactory INSTANCE = new HttpURLConnectionFactory();

    protected static final int CONNECT_TIMEOUT_MILLIS = 10000;
    protected static final int READ_TIMEOUT_MILLIS = 5000;

    private static final String USER_AGENT = Constant.USER_AGENT;
    private static final String REQUEST_METHOD_POST = "POST";

    static {
        URLConnection.setContentHandlerFactory(new ContentHandlerFactoryImpl());
        CookieManager manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(manager);
    }

    private HttpURLConnectionFactory() {
        super();
    }

    public static final HttpURLConnectionFactory getInstance() {
        return INSTANCE;
    }

    public HttpURLConnection createHttpURLConnection(URL url,
            HttpMethod method,
            Map<String, Object> headers,
            Map<String, Object> parameters) throws IOException {
        if (HttpMethod.GET.equals(method)) {
            return createGetConnection(url, headers);
        } else if (HttpMethod.POST.equals(method)) {
            return createPostConnection(url, headers, parameters);
        } else {
            throw new IllegalArgumentException("暂不支持GET、POST外的请求方式！");
        }
    }

    protected HttpURLConnection createGetConnection(URL url,
            Map<String, Object> headers) throws IOException {
        return customizeHttpURLConnection(url, headers);
    }

    protected HttpURLConnection createPostConnection(URL url,
            Map<String, Object> headers,
            Map<String, Object> parameters) throws IOException {
        HttpURLConnection httpURLConnection = customizeHttpURLConnection(url, headers);
        httpURLConnection.setRequestMethod(REQUEST_METHOD_POST);
        if (!httpURLConnection.getDoInput()) {
            httpURLConnection.setDoInput(true);
        }
        setupRequestBody(httpURLConnection, parameters);
        return httpURLConnection;
    }

    private void setupRequestBody(HttpURLConnection httpURLConnection, Map<String, Object> parameters) {
        Set<Map.Entry<String, Object>> entries = parameters == null ? Collections.emptySet() : parameters.entrySet();
        String body = entries.stream()
                .filter(entry -> Objects.nonNull(entry.getKey()))
                .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("&"));
        if (StringUtils.isNotEmpty(body)) {
            String charsetName = HttpUtils.getEncodingFromContentType(httpURLConnection.getRequestProperty("Content-Type"));
            try (OutputStreamWriter osw = new OutputStreamWriter(httpURLConnection.getOutputStream(), charsetName)) {
                osw.write(body);
                osw.flush();
            } catch (IOException ioe) {
                throw new IllegalStateException("设置请求参数出错！", ioe);
            }
        }
    }

    private HttpURLConnection customizeHttpURLConnection(URL url,
            Map<String, Object> headers) throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        httpURLConnection.setReadTimeout(READ_TIMEOUT_MILLIS);
        httpURLConnection.setUseCaches(false);
        httpURLConnection.addRequestProperty("User-Agent", USER_AGENT);
        String site = String.format("%s://%s", url.getProtocol(), url.getAuthority());
        httpURLConnection.addRequestProperty("Origin", site);
        httpURLConnection.addRequestProperty("Referer", String.format("%s/", site));
        httpURLConnection.addRequestProperty("Sec-Fetch-Dest", "empty");
        httpURLConnection.addRequestProperty("Sec-Fetch-Mode", "cors");
        httpURLConnection.addRequestProperty("Sec-Fetch-Site", "same-site");
        Set<Map.Entry<String, Object>> entries = headers == null ? Collections.emptySet() : headers.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            httpURLConnection.addRequestProperty(entry.getKey(), entry.getValue().toString());
        }
        return httpURLConnection;
    }

}
