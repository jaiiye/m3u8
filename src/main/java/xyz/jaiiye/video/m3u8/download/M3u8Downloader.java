/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.download;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import io.lindstrom.m3u8.model.KeyMethod;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.model.SegmentKey;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.lindstrom.m3u8.parser.ParsingMode;
import io.lindstrom.m3u8.parser.PlaylistParserException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import xyz.jaiiye.video.m3u8.Constant;
import xyz.jaiiye.video.m3u8.Log;
import xyz.jaiiye.video.m3u8.M3u8Exception;
import xyz.jaiiye.video.m3u8.StringUtils;
import xyz.jaiiye.video.m3u8.ThreadUtils;
import xyz.jaiiye.video.m3u8.net.RetryHttpClient;

/**
 * @author liyaling
 * @author jaiiye
 * @email ts_liyaling@qq.com
 * @date 2019/12/14 16:05
 */
public final class M3u8Downloader {

    class M3u8Key implements Serializable {

        //解密算法名称
        private final String method;
        //密钥字节
        private final byte[] keyBytes;
        //IV
        private final byte[] ivBytes;

        public M3u8Key(String method, byte[] keyBytes, byte[] ivBytes) {
            this.method = method;
            this.keyBytes = keyBytes;
            this.ivBytes = ivBytes;
        }

        public String getMethod() {
            return method;
        }

        public byte[] getKeyBytes() {
            return keyBytes;
        }

        public byte[] getIvBytes() {
            return ivBytes;
        }
    }

    class FileTask implements Callable<File> {

        private final String fragmentUrl;
        private final String pathname;
        private final M3u8Key m3u8Key;

        public FileTask(String fragmentUrl, String pathname, M3u8Key m3u8Key) {
            super();
            this.fragmentUrl = fragmentUrl;
            this.pathname = pathname;
            this.m3u8Key = m3u8Key;
        }

        public String getFragmentUrl() {
            return fragmentUrl;
        }

        public String getPathname() {
            return pathname;
        }

        public M3u8Key getM3u8Key() {
            return m3u8Key;
        }

        @Override
        public File call() throws Exception {
            RetryHttpClient<File> retryHttpClient = customizeRetryHttpClient();
            return retryHttpClient.get(fragmentUrl);
        }

        protected final RetryHttpClient<File> customizeRetryHttpClient() {
            RetryHttpClient<File> retryHttpClient = new RetryHttpClient((httpURLConnection) -> {
                final File file = new File(pathname);
                try (InputStream is = (InputStream) httpURLConnection.getContent();
                        OutputStream os = createOutputStream(file)) {
                    byte[] buffer = new byte[Constant.MEDIA_BYTE_COUNT];
                    int length;
                    while ((length = is.read(buffer)) != -1) {
                        os.write(buffer, 0, length);
                        downloadBytes.addAndGet(length);
                    }
                    os.flush();
                }
                return file;
            });
            return retryHttpClient;
        }

        private OutputStream createOutputStream(File file) {
            try {
                FileOutputStream fos = new FileOutputStream(file);
                if (m3u8Key != null) {
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
                    SecretKeySpec secretKeySpec = new SecretKeySpec(m3u8Key.getKeyBytes(), "AES");
                    if (m3u8Key.getIvBytes().length > 0) {
                        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(m3u8Key.getIvBytes()));
                    } else {
                        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
                    }
                    return new CipherOutputStream(fos, cipher);
                }
                return fos;
            } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException ex) {
                throw new M3u8Exception(String.format("解密ts片段出错：%s", ex.getMessage()));
            }
        }
    }

    class StatThread extends Thread {

        public StatThread() {
            super();
            start();
        }

        @Override
        public void run() {
            for (DownloadListener downloadListener : listeners) {
                downloadListener.start();
            }
            //轮询是否下载成功
            while (!futures.isEmpty()) {
                try {
                    long latestDownloadBytes = downloadBytes.longValue();
                    TimeUnit.SECONDS.sleep(intervalOfSeconds);
                    long recentDownloadBytesPerSecond = (long) (downloadBytes.longValue() - latestDownloadBytes) / intervalOfSeconds;
                    for (DownloadListener downloadListener : listeners) {
                        downloadListener.stat(downloadUrl,
                                taskTotal,
                                finishedFiles.size(),
                                String.format("%s/s", StringUtils.humanReadableByteCount(recentDownloadBytesPerSecond)));
                    }
                } catch (InterruptedException ex) {
                    System.err.println("处理监听出错！" + ex.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
            for (DownloadListener downloadListener : listeners) {
                downloadListener.end();
            }
        }
    }

    //需要下载的m3u8链接
    private final String downloadUrl;
    //线程数
    private int threadCount;
    //下载文件存储目录
    private String downloadBaseDir;
    //合并后的视频文件名称
    private String filename;
    //是否合并
    private boolean merged;
    //合并后的文件存储目录
    private String storageBaseDir;
    //任务数量
    private volatile int taskTotal;
    //任务
    private final List<Future<File>> futures;
    //解密后的片段
    private final Set<File> finishedFiles;
    //已经下载的文件大小
    private final AtomicLong downloadBytes;
    //监听事件
    private final Set<DownloadListener> listeners;
    //监听间隔
    private volatile int intervalOfSeconds;

    public M3u8Downloader(String downloadUrl) {
        super();
        this.downloadUrl = downloadUrl;
        this.taskTotal = 0;
        this.futures = new ArrayList<>();
        this.finishedFiles = new ConcurrentSkipListSet<>((o1, o2) -> {
            int diff = o1.getName().length() - o2.getName().length();
            if (diff == 0) {
                return o1.getName().compareTo(o2.getName());
            }
            return diff;
        });
        this.downloadBytes = new AtomicLong();
        this.listeners = new HashSet<>(5);
        this.intervalOfSeconds = 1;
        init();
    }

    protected void init() {
        setThreadCount(Constant.DEFAULT_THREAD_COUNT);
        addListener(new DefaultDownloadListener());
    }

    public URI getBaseURI() {
        return URI.create(downloadUrl);
    }

    public void addListener(DownloadListener downloadListener) {
        getListeners().add(downloadListener);
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public String getDownloadBaseDir() {
        return downloadBaseDir;
    }

    public void setDownloadBaseDir(String downloadBaseDir) {
        this.downloadBaseDir = downloadBaseDir;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public String getStorageBaseDir() {
        return storageBaseDir;
    }

    public void setStorageBaseDir(String storageBaseDir) {
        this.storageBaseDir = storageBaseDir;
    }

    public int getTaskTotal() {
        return taskTotal;
    }

    public void setTaskTotal(int taskTotal) {
        this.taskTotal = taskTotal;
    }

    public int getIntervalOfSeconds() {
        return intervalOfSeconds;
    }

    public void setIntervalOfSeconds(int intervalOfSeconds) {
        this.intervalOfSeconds = intervalOfSeconds;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public List<Future<File>> getFutures() {
        return futures;
    }

    public Set<File> getFinishedFiles() {
        return finishedFiles;
    }

    public AtomicLong getDownloadBytes() {
        return downloadBytes;
    }

    public Set<DownloadListener> getListeners() {
        return listeners;
    }

    /**
     * 开始下载视频
     */
    public void start() {
        checkField();
        startDownload();
    }

    /**
     * 字段校验
     */
    private void checkField() {
        if (threadCount <= 0) {
            throw new M3u8Exception("同时下载线程数只能大于0！");
        }
        if (StringUtils.isEmpty(downloadBaseDir)) {
            throw new M3u8Exception("视频下载目录不能为空！");
        }
        if (StringUtils.isEmpty(storageBaseDir)) {
            throw new M3u8Exception("视频存储目录不能为空！");
        }
        if (merged && StringUtils.isEmpty(filename)) {
            throw new M3u8Exception("视频名称不能为空！");
        }
        if (intervalOfSeconds <= 0) {
            throw new M3u8Exception("监听间隔时间只能大于0！");
        }
    }

    /**
     * 下载视频
     */
    private void startDownload() {
        //线程池
        final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(threadCount);
        //处理m3u8文件，创建下载、解密任务
        RetryHttpClient<String> retryHttpClient = RetryHttpClient.getDefaultRetryHttpClient();
        String result = retryHttpClient.get(downloadUrl);
        String m3u8Content = transformLineSeparator(result);
        if (m3u8Content.contains(Constant.M3U8_SUFFIX)) {
            throw new M3u8Exception("暂不支持 Master PlayList下载！");
        } else {
            try {
                MediaPlaylistParser mediaPlaylistParser = new MediaPlaylistParser(ParsingMode.LENIENT);
                MediaPlaylist mediaPlaylist = mediaPlaylistParser.readPlaylist(m3u8Content);
                List<MediaSegment> mediaSegments = mediaPlaylist.mediaSegments();
                //设置任务数量
                setTaskTotal(mediaSegments.size());
                M3u8Key m3u8Key = null;
                for (MediaSegment mediaSegment : mediaSegments) {
                    Optional<SegmentKey> optionalSegmentKey = mediaSegment.segmentKey();
                    if (optionalSegmentKey.isPresent()) {
                        m3u8Key = createM3u8Key(optionalSegmentKey);
                    }
                    URI mediaSegmentUri = URI.create(mediaSegment.uri());
                    String fragmentUrl = mediaSegmentUri.isAbsolute() ? mediaSegmentUri.toString() : getBaseURI().resolve(mediaSegmentUri).toString();
                    File mediaSegmentFile = createMediaSegmentFile(mediaSegmentUri);
                    FileTask task = new FileTask(fragmentUrl, mediaSegmentFile.getPath(), m3u8Key);
                    futures.add(fixedThreadPool.submit(task));
                }
            } catch (PlaylistParserException ex) {
                System.err.println("处理M3U8链接出错！" + ex.getMessage());
            }
        }
        Thread statThread = new StatThread();
        //处理完成任务
        futureTasks:
        while (!futures.isEmpty()) {
            for (Iterator<Future<File>> iterator = futures.iterator(); iterator.hasNext();) {
                Future<File> future = iterator.next();
                if (future.isDone()) {
                    if (!future.isCancelled()) {
                        try {
                            finishedFiles.add(future.get());
                        } catch (InterruptedException ie) {
                            System.err.println("下载、解密任务中断！" + ie.getMessage());
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException ee) {
                            System.err.println("下载、解密任务出错！" + ee.getMessage());
                            break futureTasks;
                        }
                    }
                    iterator.remove();
                }
            }
        }
        //停止所有任务
        List<Runnable> runnables = fixedThreadPool.shutdownNow();
        if (!runnables.isEmpty()) {
            Log.e("取消下载视频ts片段" + runnables.size() + "个");
            futures.clear();
        }
        ThreadUtils.waitThread(statThread);
        //处理文件
        if (finishedFiles.size() >= taskTotal) {
            Log.i(String.format("视频ts片段全部下载成功（%d个，%s）", finishedFiles.size(), StringUtils.humanReadableByteCount(downloadBytes.longValue())));
            boolean cleanup = merged ? mergeDownloadFiles().exists() : moveDownloadFiles();
            if (cleanup) {
                deleteDownloadFiles();
            }
        } else {
            Log.e("视频共有ts片段" + taskTotal + "个，实际下载" + finishedFiles.size() + "个，部分文件下载失败，无法执行后续操作");
        }
    }

    private String transformLineSeparator(String text) {
        final StringBuilder sb = new StringBuilder();
        try (final BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException ex) {
            System.err.println("转换换行符出错！" + ex.getMessage());
        }
        return sb.toString();
    }

    //创建m3u8 key
    protected M3u8Key createM3u8Key(Optional<SegmentKey> optionalSegmentKey) {
        if (optionalSegmentKey.isPresent()) {
            SegmentKey segmentKey = optionalSegmentKey.get();
            if (KeyMethod.AES_128.equals(segmentKey.method())) {
                RetryHttpClient<byte[]> retryHttpClient = new RetryHttpClient<>((httpURLConnection) -> {
                    final byte[] bytes = new byte[16];
                    try (InputStream is = httpURLConnection.getInputStream()) {
                        is.read(bytes);
                    }
                    return bytes;
                });
                URI keyUri = URI.create(segmentKey.uri().get());
                String keyUrl = keyUri.isAbsolute() ? keyUri.toString() : getBaseURI().resolve(keyUri).toString();
                byte[] keyBytes = retryHttpClient.get(keyUrl);
                byte[] ivBytes = parseIV(segmentKey);
                M3u8Key m3u8Key = new M3u8Key(segmentKey.method().toString(), keyBytes, ivBytes);
                return m3u8Key;
            }
        }
        return null;
    }

    /**
     * iv解码
     *
     * @param segmentKey
     * @return
     */
    protected byte[] parseIV(SegmentKey segmentKey) {
        if (segmentKey.iv().isPresent()) {
            String ivStr = segmentKey.iv().get();
            if (ivStr.startsWith(Constant.HEX_PREFIX)) {
                String expected = ivStr.substring(Constant.HEX_PREFIX.length());
                return StringUtils.decodeHex(expected);
            } else {
                return ivStr.getBytes(StandardCharsets.UTF_8);
            }
        }
        return new byte[0];
    }

    private File createMediaSegmentFile(URI mediaSegmentUri) {
        String filename = getFilename(mediaSegmentUri.getPath());
        return createFile(getDownloadBaseDir(), filename);
    }

    protected String getFilename(String path) {
        if (path == null || path.isEmpty()) {
            throw new M3u8Exception("无法获取文件名称！");
        }
        int pos = path.lastIndexOf("/") + 1;
        return path.substring(pos);
    }

    /**
     * 合并并删除下载好的ts文件（片段）
     */
    protected File mergeDownloadFiles() {
        String expectedFilename = filename.endsWith(Constant.MP4_SUFFIX) ? filename : String.format("%s%s", filename, Constant.MP4_SUFFIX);
        File videoFile = createFile(storageBaseDir, expectedFilename);
        Log.i("合并文件，文件名称：" + videoFile.getName());
        File listFile = generateListFile();
        //https://trac.ffmpeg.org/wiki/Concatenate
        try {
            FFmpeg.atPath()
                    .addArguments("-f", "concat")
                    .addArguments("-safe", "0")
                    .addArguments("-i", listFile.getCanonicalPath())
                    .addArguments("-c", "copy")
                    .setOverwriteOutput(true)
                    .addOutput(UrlOutput.toUrl(videoFile.getCanonicalPath()))
                    .execute();
            listFile.delete();
        } catch (Exception ex) {
            System.err.println("合并文件出错！" + ex.getMessage());
        }
        return videoFile;
    }

    private File generateListFile() {
        File file = createFile(downloadBaseDir, "list.txt");
        try (final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {
            for (File f : finishedFiles) {
                writer.write(String.format("file '%s'%n", f.getCanonicalPath()));
            }
            writer.flush();
        } catch (IOException ex) {
            System.err.println("生成文件列表出错！" + ex.getMessage());
        }
        return file;
    }

    protected boolean moveDownloadFiles() {
        File source = new File(downloadBaseDir);
        File target = new File(storageBaseDir);
        Log.i("移动文件，相关文件：[" + source + "]->[" + target + "]");
        return source.renameTo(target);
    }

    private boolean deleteDownloadFiles() {
        Log.i("开始删除所有下载文件...");
        //删除文件
        for (File f : finishedFiles) {
            f.delete();
        }
        File tmpDir = new File(downloadBaseDir);
        return tmpDir.delete();
    }

    protected File createFile(String parent, String filename) {
        try {
            File file = new File(parent, filename);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            return file;
        } catch (IOException ioe) {
            throw new M3u8Exception("创建文件失败！", ioe);
        }
    }

}
