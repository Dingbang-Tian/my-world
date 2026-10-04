package com.dingbang.myworld.aiapp.codegen.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 持续消费进程输出，流式归档并保留有界首尾预览。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
final class CommandOutputCapture implements Runnable {
    /**
     * 模型预览首尾各保留的字节数。
     */
    private static final int PREVIEW_BYTES = 2048;
    /**
     * 合并后的标准输出流。
     */
    private final InputStream stream;
    /** 流式保存完整有界输出的文件句柄。 */
    private final LocalToolOutputStore.Writer archive;
    /**
     * 已保留的首部预览字节。
     */
    private final java.io.ByteArrayOutputStream prefix = new java.io.ByteArrayOutputStream(PREVIEW_BYTES);
    /** 最近的进程输出字节。 */
    private final byte[] tail = new byte[PREVIEW_BYTES];
    /** 尾部环形缓冲区下一写入位置。 */
    private int tailPosition;
    /** 尾部环形缓冲区有效字节数。 */
    private int tailSize;
    /** 已消费的进程输出总字节数。 */
    private long consumedBytes;
    /**
     * 是否发生截断。
     */
    private boolean truncated;
    /** 持久化期间发生的错误。 */
    private IOException failure;

    /**
     * 绑定进程输出流和外置持久化句柄。
     *
     * @param stream 进程输出流
     * @param archive 外置保存句柄
     */
    CommandOutputCapture(InputStream stream, LocalToolOutputStore.Writer archive) {
        this.stream = stream;
        this.archive = archive;
    }

    /**
     * 持续读取直到进程关闭输出流。
     */
    @Override
    public void run() {
        // 每次读取的缓冲区。
        byte[] buffer = new byte[4096];
        try (archive) {
            // 当前读取字节数。
            int count;
            while (true) {
                try {
                    count = stream.read(buffer);
                } catch (IOException closedAfterCancellation) {
                    break;
                }
                if (count == -1) break;
                synchronized (this) {
                    /** 本次保留到首部的字节数。 */
                    int retain = Math.min(count, PREVIEW_BYTES - prefix.size());
                    prefix.write(buffer, 0, retain);
                    /** 首部之后的字节写入尾部环形缓冲区。 */
                    for (int index = retain; index < count; index++) {
                        tail[tailPosition] = buffer[index];
                        tailPosition = (tailPosition + 1) % PREVIEW_BYTES;
                        tailSize = Math.min(PREVIEW_BYTES, tailSize + 1);
                    }
                    consumedBytes += count;
                    truncated = consumedBytes > 2L * PREVIEW_BYTES;
                }
                archive.write(buffer, 0, count);
            }
        } catch (IOException exception) {
            synchronized (this) {
                failure = exception;
            }
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // 进程取消时可能已经关闭输出流。
            }
        }
    }

    /**
     * 获取当前输出快照。
     *
     * @return UTF-8 文本
     */
    synchronized String text() {
        /** 尾部最早保存字节的位置。 */
        int start = (tailPosition - tailSize + PREVIEW_BYTES) % PREVIEW_BYTES;
        /** 按原始顺序展开的尾部输出。 */
        byte[] orderedTail = new byte[tailSize];
        for (int index = 0; index < tailSize; index++) {
            orderedTail[index] = tail[(start + index) % PREVIEW_BYTES];
        }
        return prefix.toString(StandardCharsets.UTF_8)
                + (truncated ? "\n[中间输出已省略，可用 outputId 分页读取]\n" : "")
                + new String(orderedTail, StandardCharsets.UTF_8);
    }

    /**
     * 返回截断标志。
     *
     * @return 输出超限时为 true
     */
    synchronized boolean truncated() {
        return truncated;
    }

    /**
     * 返回外置日志标识及大小；写入失败时拒绝伪称日志完整。
     *
     * @return 可供回读的输出元数据
     * @throws IOException 持久化失败时
     */
    synchronized String reference() throws IOException {
        if (failure != null) throw failure;
        return "outputId=" + archive.outputId() + " storedBytes=" + archive.storedBytes()
                + " archiveTruncated=" + archive.truncated() + "\n";
    }
}
