package com.dingbang.myworld.aiapp.codegen.tool;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 持续消费进程输出并只保留固定前缀。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class CommandOutputCapture implements Runnable {
    /**
     * 最多保留的进程输出字节数。
     */
    private static final int MAX_OUTPUT_BYTES = 16_000;
    /**
     * 合并后的标准输出流。
     */
    private final InputStream stream;
    /**
     * 已保留的输出字节。
     */
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(MAX_OUTPUT_BYTES);
    /**
     * 是否发生截断。
     */
    private boolean truncated;

    /**
     * 持续读取直到进程关闭输出流。
     */
    @Override
    public void run() {
        // 每次读取的缓冲区。
        byte[] buffer = new byte[4096];
        try (stream) {
            // 当前读取字节数。
            int count;
            while ((count = stream.read(buffer)) != -1) {
                synchronized (this) {
                    // 本次仍可保留的字节数。
                    int retain = Math.min(count, MAX_OUTPUT_BYTES - bytes.size());
                    bytes.write(buffer, 0, retain);
                    truncated |= retain < count;
                }
            }
        } catch (IOException ignored) {
            // 取消或超时关闭输出流时，已读取的前缀仍可返回。
        }
    }

    /**
     * 获取当前输出快照。
     *
     * @return UTF-8 文本
     */
    synchronized String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * 返回截断标志。
     *
     * @return 输出超限时为 true
     */
    synchronized boolean truncated() {
        return truncated;
    }
}
