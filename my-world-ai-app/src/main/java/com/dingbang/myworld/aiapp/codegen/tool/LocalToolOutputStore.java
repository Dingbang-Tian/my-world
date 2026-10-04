package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.common.utils.lang.HashUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * 将命令输出按会话隔离写入本地目录，并提供有界分页读取。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
public final class LocalToolOutputStore {
    /** 单次命令最多保留的完整输出字节数。 */
    private static final long MAX_STORED_BYTES = 20L * 1024 * 1024;
    /** 单次回读允许的最大字节数。 */
    private static final int MAX_READ_BYTES = 8192;
    /** 管理员配置的本地输出目录。 */
    private final Path root;

    /**
     * 使用可信目录存放工具输出。
     *
     * @param root 持久化输出目录
     */
    public LocalToolOutputStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * 为一个会话创建独立输出文件。
     *
     * @param sessionId 可信会话标识
     * @return 可持续写入的输出句柄
     * @throws IOException 目录或文件创建失败时
     */
    public Writer create(String sessionId) throws IOException {
        /** 当前会话的隔离目录。 */
        Path directory = root.resolve(HashUtils.sha256(sessionId));
        /** 是否由当前调用创建根目录。 */
        boolean rootCreated = !Files.exists(root, LinkOption.NOFOLLOW_LINKS);
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("输出目录不可为符号链接");
        /** 是否由当前调用创建会话目录。 */
        boolean directoryCreated = !Files.exists(directory, LinkOption.NOFOLLOW_LINKS);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("输出目录不可为符号链接");
        }
        if (rootCreated) restrictDirectory(root);
        else requirePrivateDirectory(root);
        if (directoryCreated) restrictDirectory(directory);
        else requirePrivateDirectory(directory);
        /** 不可由模型指定的输出标识。 */
        String outputId = UUID.randomUUID().toString();
        /** 新建输出文件，不覆盖任何已有内容。 */
        Path file = directory.resolve(outputId + ".log");
        /** 使用排他创建并限制其他系统用户访问。 */
        OutputStream stream = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统由系统目录权限保护。
        } catch (IOException exception) {
            stream.close();
            throw exception;
        }
        return new Writer(outputId, stream);
    }

    /**
     * 按字节偏移读取同一会话的工具输出。
     *
     * @param sessionId 可信会话标识
     * @param outputId 不可猜测的输出标识
     * @param offset 起始字节偏移
     * @param limit 最多读取字节数
     * @return 含下一偏移的有界输出片段
     * @throws IOException 输出不存在或读取失败时
     */
    public Chunk read(String sessionId, String outputId, long offset, int limit) throws IOException {
        if (outputId == null || !outputId.matches("[0-9a-fA-F-]{36}")
                || offset < 0 || limit < 4 || limit > MAX_READ_BYTES) {
            throw new IllegalArgumentException("输出标识、偏移或读取长度无效");
        }
        /** 已按可信会话隔离的文件路径。 */
        Path file = root.resolve(HashUtils.sha256(sessionId)).resolve(outputId + ".log");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("输出不存在或不属于当前会话");
        }
        /** 使用 NOFOLLOW_LINKS 防止目标在读取时变为符号链接。 */
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            /** 当前文件实际大小。 */
            long size = channel.size();
            if (offset > size) throw new IllegalArgumentException("读取偏移超出输出长度");
            /** 最多读取指定长度的字节缓冲区。 */
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(limit, size - offset));
            channel.position(offset);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
            /** 实际读取的字节数。 */
            int count = buffer.position();
            if (offset + count < size && count > 0) {
                /** 页尾最后一个 UTF-8 字符的起始位置。 */
                int leading = count - 1;
                while (leading > 0 && (buffer.array()[leading] & 0xC0) == 0x80) leading--;
                /** 该字符应该包含的字节数。 */
                int required = utf8Width(buffer.array()[leading]);
                if (count - leading < required) count = leading;
            }
            return new Chunk(outputId, offset, offset + count, size,
                    new String(buffer.array(), 0, count, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 将私有日志目录限制为当前操作系统用户可访问。
     *
     * @param directory 日志目录
     * @throws IOException 权限设置失败时
     */
    private static void restrictDirectory(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows 等文件系统通过原有 ACL 管理权限。
        }
    }

    /**
     * 拒绝使用其他操作系统用户可访问的既有日志目录。
     *
     * @param directory 既有目录
     * @throws IOException 目录权限不够私有时
     */
    private static void requirePrivateDirectory(Path directory) throws IOException {
        try {
            /** 已有目录的 POSIX 权限。 */
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
            if (permissions.stream().anyMatch(value -> value.name().startsWith("GROUP_")
                    || value.name().startsWith("OTHERS_"))) {
                throw new IOException("输出目录必须仅对当前用户开放");
            }
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统依赖原有 ACL。
        }
    }

    /**
     * 根据首字节判断 UTF-8 字符应占的字节数。
     *
     * @param first 首字节
     * @return 字符宽度
     */
    private static int utf8Width(byte first) {
        /** 首字节的无符号表示。 */
        int value = first & 0xFF;
        if (value < 0x80) return 1;
        if ((value & 0xE0) == 0xC0) return 2;
        if ((value & 0xF0) == 0xE0) return 3;
        if ((value & 0xF8) == 0xF0) return 4;
        return 1;
    }

    /**
     * 已存储输出的分页信息。
     *
     * @param outputId 输出标识
     * @param offset 本页起始偏移
     * @param nextOffset 下页起始偏移
     * @param storedBytes 已存储字节数
     * @param text 本页文本
     * @author Sebastian
     * @since 2026/10/05
     */
    public record Chunk(String outputId, long offset, long nextOffset, long storedBytes, String text) { }

    /**
     * 限制单条输出存储量的流式句柄。
     *
     * @author Sebastian
     * @since 2026/10/05
     */
    public static final class Writer extends OutputStream {
        /** 关联模型可见结果的输出标识。 */
        private final String outputId;
        /** 实际持久化文件输出流。 */
        private final OutputStream delegate;
        /** 已持久化的字节数。 */
        private long storedBytes;
        /** 输出是否超过存储上限。 */
        private boolean truncated;

        /**
         * 绑定新建输出文件。
         *
         * @param outputId 输出标识
         * @param delegate 文件流
         */
        private Writer(String outputId, OutputStream delegate) {
            this.outputId = outputId;
            this.delegate = delegate;
        }

        /**
         * 流式保存单个字节。
         *
         * @param value 字节值
         * @throws IOException 写入失败时
         */
        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        /**
         * 持续消费全部输出，仅持久化上限以内的字节。
         *
         * @param value 原始字节
         * @param offset 起始偏移
         * @param length 字节数
         * @throws IOException 持久化失败时
         */
        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            /** 本次可保存的字节数。 */
            int retained = (int) Math.min(length, MAX_STORED_BYTES - storedBytes);
            if (retained > 0) delegate.write(value, offset, retained);
            storedBytes += retained;
            truncated |= retained < length;
        }

        /**
         * 关闭输出文件并释放句柄。
         *
         * @throws IOException 关闭失败时
         */
        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /**
         * 返回模型可引用的输出标识。
         *
         * @return 输出标识
         */
        public String outputId() { return outputId; }

        /**
         * 返回实际保存的字节数。
         *
         * @return 保存字节数
         */
        public long storedBytes() { return storedBytes; }

        /**
         * 返回存储上限是否被触发。
         *
         * @return true 表示后续字节未保存
         */
        public boolean truncated() { return truncated; }
    }
}
