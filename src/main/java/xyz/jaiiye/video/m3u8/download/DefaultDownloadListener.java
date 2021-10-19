/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.download;

/**
 * 默认下载监听
 */
public final class DefaultDownloadListener implements DownloadListener {

    @Override
    public void start() {
        System.out.println("开始下载");
    }

    @Override
    public void stat(String downloadUrl, int sum, int finished, String speedPerSecond) {
        System.out.printf("总共需要下载文件：%4d，已完成：%4d，剩余：%4d，当前下载速度：%s%n",
                sum,
                finished,
                (sum - finished),
                speedPerSecond);
    }

    @Override
    public void end() {
        System.out.println("下载完毕");
    }

}
