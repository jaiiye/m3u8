/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.net;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 *
 * @author jaiiye
 * @param <T>
 */
@FunctionalInterface
public interface HttpURLConnectionHandler<T> {

    T handle(HttpURLConnection httpURLConnection) throws IOException;

}
