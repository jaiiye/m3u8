package xyz.jaiiye.video.m3u8.download;

public interface DownloadListener {

    void start();

    void stat(String downloadUrl, int sum, int finished, String speedPerSecond);

    void end();

}
