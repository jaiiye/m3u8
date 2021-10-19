/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import xyz.jaiiye.video.m3u8.Constant;

/**
 *
 * @author jaiiye
 * @param <T>
 */
public final class RetryHttpClient<T> {

    private final HttpURLConnectionHandler<T> httpURLConnectionHandler;
    //重试次数
    private final int retryCount;

    public RetryHttpClient(HttpURLConnectionHandler<T> httpURLConnectionHandler) {
        this(httpURLConnectionHandler, Constant.DEFAULT_RETRY_TIMES);
    }

    public RetryHttpClient(HttpURLConnectionHandler<T> httpURLConnectionHandler, int retryCount) {
        super();
        this.httpURLConnectionHandler = httpURLConnectionHandler;
        this.retryCount = retryCount;
    }

    protected T exchange(String url, HttpMethod method,
            Map<String, Object> headers,
            Map<String, Object> parameters) {
        int reconnectionTimes = 0;//链接当前重试次数
        while (true) {
            try {
                HttpURLConnection httpURLConnection = HttpURLConnectionFactory.getInstance().createHttpURLConnection(new URL(url), method, headers, parameters);
                T result = httpURLConnectionHandler.handle(httpURLConnection);
                httpURLConnection.disconnect();
                return result;
            } catch (IOException ex) {
                System.err.println("请求链接（" + url + "）出错！" + ex.getMessage());
                if (++reconnectionTimes > retryCount) {
                    throw new CustomConnectException(ex.getMessage());
                } else {
                    HttpUtils.waitForReconnection(reconnectionTimes);
                }
            }
        }
    }

    public T get(String url) {
        return get(url, Collections.emptyMap());
    }

    public T get(String url, Map<String, Object> headers) {
        return exchange(url, HttpMethod.GET, headers, Collections.emptyMap());
    }

    public T post(String url) {
        return post(url, Collections.emptyMap(), Collections.emptyMap());
    }

    public T post(String url,
            Map<String, Object> headers,
            Map<String, Object> parameters) {
        return exchange(url, HttpMethod.POST, headers, parameters);
    }

    public static final RetryHttpClient getDefaultRetryHttpClient() {
        return new RetryHttpClient((httpURLConnection) -> {
            return httpURLConnection.getContent();
        });
    }

}
