/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8;

/**
 *
 * @author jaiiye
 */
public final class ThreadUtils {

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            System.err.println("线程休眠中断！" + ie.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public static void waitThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException ie) {
            System.err.println("等待线程出错！" + ie.getMessage());
            Thread.currentThread().interrupt();
        }
    }

}
