package com.dingbang.myworld.aiapp.codegen.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 将文件工具的相对路径限制在固定工作目录，并拒绝符号链接穿越。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class WorkspacePolicy {
    /** 已存在且已解析真实路径的工作目录。 */
    private final Path root;

    /**
     * 固定可信工作目录。
     *
     * @param workspace 已存在的工作目录
     */
    public WorkspacePolicy(Path workspace) {
        try {
            this.root = Objects.requireNonNull(workspace, "工作目录不能为空").toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("工作目录不是目录");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("工作目录不可访问", exception);
        }
    }

    /**
     * 解析模型给出的路径，并拒绝绝对路径、越界路径及任一符号链接。
     *
     * @param relative 工作目录内的相对路径，空字符串表示根目录
     * @return 已校验的路径
     */
    public Path resolve(String relative) {
        if (relative == null || relative.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("路径无效");
        }
        try {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) {
                throw new IllegalArgumentException("工作目录真实路径已改变");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("工作目录不可访问", exception);
        }
        /** 用户提交的相对路径。 */
        Path input = Path.of(relative);
        if (input.isAbsolute()) {
            throw new IllegalArgumentException("只允许相对路径");
        }
        /** 标准化后的目标路径。 */
        Path result = root.resolve(input).normalize();
        if (!result.startsWith(root)) {
            throw new IllegalArgumentException("路径超出工作目录");
        }
        /** 当前检查的每一级路径。 */
        Path current = root;
        for (Path segment : root.relativize(result)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("不允许符号链接路径");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (!current.toRealPath().startsWith(root)) {
                        throw new IllegalArgumentException("真实路径超出工作目录");
                    }
                } catch (IOException exception) {
                    throw new IllegalArgumentException("路径不可访问", exception);
                }
            }
        }
        return result;
    }

    /**
     * 返回工作目录真实路径。
     *
     * @return 工作目录
     */
    public Path root() {
        return root;
    }
}
