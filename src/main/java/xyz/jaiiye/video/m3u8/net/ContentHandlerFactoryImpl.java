/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.net;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jaiiye
 */
public class ContentHandlerFactoryImpl implements ContentHandlerFactory {

    private final List<String> mimeTypes = Arrays.asList("text/plain",
            "text/html",
            "text/css",
            "application/javascript",
            "application/json",
            "application/xml",
            "application/x-mpegURL",
            "application/vnd.apple.mpegURL");

    @Override
    public ContentHandler createContentHandler(String mimetype) {

        if (mimeTypes.stream().anyMatch(s -> s.equalsIgnoreCase(mimetype))) {
            return new ContentHandlerImpl(true);
        } else {
            return new ContentHandlerImpl(false);
        }

    }

    class ContentHandlerImpl extends ContentHandler {

        private boolean transform = false;

        public ContentHandlerImpl(boolean transform) {
            this.transform = transform;
        }

        @Override
        public Object getContent(URLConnection connection) throws IOException {
            if (!transform) {
                return connection.getInputStream();
            } else {
                String encoding = HttpUtils.getEncodingFromContentType(connection.getContentType());
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), encoding)) {
                    StringBuilder content = new StringBuilder();
                    char[] buffer = new char[1024];
                    int length;
                    while ((length = reader.read(buffer)) != -1) {
                        content.append(buffer, 0, length);
                    }
                    return content.toString();
                }
            }
        }
    }

}
