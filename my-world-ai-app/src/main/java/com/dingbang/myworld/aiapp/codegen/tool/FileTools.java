package com.dingbang.myworld.aiapp.codegen.tool;

import com.dingbang.myworld.agent.persistence.RunJournal;
import com.dingbang.myworld.aiframework.api.ExecutionControlException;

import com.dingbang.myworld.agent.tool.Tool;
import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutionResult;
import com.dingbang.myworld.aiapp.codegen.api.FileArtifact;
import com.dingbang.myworld.common.utils.lang.HashUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 构造受同一工作目录策略约束的九个文件工具。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
public final class FileTools {
    /**
     * 单次文件读取的最大字节数。
     */
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    /**
     * 单次工具结果的最大字符数。
     */
    private static final int MAX_OUTPUT = 16000;
    /**
     * 一次目录搜索最多检查的文件数。
     */
    private static final int MAX_SEARCH_FILES = 1000;
    /**
     * 固定工作目录路径策略。
     */
    private final WorkspacePolicy policy;
    /**
     * 文件副作用的持久化记录器。
     */
    private final RunJournal journal;
    /**
     * 按运行标识保存成功的文件产物。
     */
    private final Map<String, List<FileArtifact>> artifacts = new ConcurrentHashMap<>();

    /**
     * 创建文件工具集合。
     *
     * @param policy 工作目录路径策略
     */
    public FileTools(WorkspacePolicy policy) {
        this(policy, RunJournal.NONE);
    }

    /**
     * 创建带有持久化产物记录的文件工具集合。
     *
     * @param policy 工作目录路径策略
     * @param journal 运行日志
     */
    public FileTools(WorkspacePolicy policy, RunJournal journal) {
        this.policy = Objects.requireNonNull(policy, "路径策略不能为空");
        this.journal = Objects.requireNonNull(journal, "运行日志不能为空");
    }

    /**
     * 返回全部九个工具，调用方再按可信配置选择授权集合。
     *
     * @return 工具列表
     */
    public List<Tool<?>> all() {
        return List.of(
                tool("list_directory_tree", "列出工作目录中的目录树", FileTreeArgs.class, this::tree),
                tool("view_file", "按行读取 UTF-8 文件并返回 SHA-256", FileViewArgs.class, this::view),
                tool("search_files", "按文件名关键词搜索文件", FileSearchArgs.class, this::searchFiles),
                tool("search_in_file", "在单个 UTF-8 文件中搜索多个关键词和上下文", FileSearchInFileArgs.class, this::searchInFile),
                tool("search_in_directory", "在目录下的 UTF-8 文件中搜索多个关键词", DirectorySearchArgs.class, this::searchInDirectory),
                tool("create_file", "创建新的 UTF-8 文件，拒绝覆盖", FileCreateArgs.class, this::create),
                tool("edit_file", "校验 SHA-256 后替换、按行插入或追加文件", FileEditArgs.class, this::edit),
                tool("move_file", "校验 SHA-256 后移动文件，拒绝覆盖目标", FileMoveArgs.class, this::move),
                tool("delete_file", "校验 SHA-256 后删除普通文件", FileDeleteArgs.class, this::delete));
    }

    /**
     * 返回某次运行已经成功的文件操作记录。
     *
     * @param runId Agent 运行标识
     * @return 不可修改的产物快照
     */
    public List<FileArtifact> artifacts(String runId) {
        // 当前运行的记录列表。
        List<FileArtifact> found = artifacts.get(runId);
        if (found == null) {
            return List.of();
        }
        synchronized (found) {
            return List.copyOf(found);
        }
    }

    /**
     * 记录成功的文件修改。
     *
     * @param context 工具执行上下文
     * @param action 操作名称
     * @param path 产物相对路径
     * @param sha256 文件版本
     */
    private void record(ToolExecutionContext context, String action, String path, String sha256) {
        try {
            journal.artifact(context.getRunId(), action, path, sha256);
        } catch (RuntimeException exception) {
            throw new ExecutionControlException("PERSISTENCE_ERROR", "文件已修改但产物记录未保存，需要核查");
        }
        artifacts.computeIfAbsent(context.getRunId(), ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(new FileArtifact(context.getRunId(), action, path, sha256));
    }

    /**
     * 创建显式描述的工具实现。
     *
     * @param name 工具名称
     * @param description 工具说明
     * @param type 参数类
     * @param action 文件操作
     * @param <P> 参数类型
     * @return Java 工具
     */
    private <P> Tool<P> tool(String name, String description, Class<P> type, FileToolAction<P> action) {
        return new FileToolAdapter<>(name, description, type, action);
    }

    /**
     * 对目录按固定顺序和深度列举，不跟随符号链接。
     *
     * @param args 目录与最大深度
     * @param context 执行上下文
     * @return 目录树
     * @throws IOException 读取失败时
     */
    private ToolExecutionResult tree(FileTreeArgs args, ToolExecutionContext context) throws IOException {
        // 目标目录。
        Path directory = directory(args.path);
        // 本次允许的层数。
        int depth = args.depth != null ? args.depth : args.maxDepth != null ? args.maxDepth : 3;
        if (depth < 0 || depth > 20) {
            throw new IllegalArgumentException("depth 必须在 0 到 20 之间");
        }
        if (depth == 0) {
            return ToolExecutionResult.text("(深度为 0，未展开目录)\n");
        }
        // 输出文本。
        StringBuilder output = new StringBuilder();
        appendTree(directory, depth, 0, output, context);
        if (output.length() == 0) {
            output.append("(空目录)\n");
        }
        return bounded(output.toString());
    }

    /**
     * 递归遍历目录并检查输出限制。
     *
     * @param directory 当前目录
     * @param maxDepth 最大深度
     * @param level 当前深度
     * @param output 输出缓存
     * @param context 取消上下文
     * @throws IOException 目录读取失败时
     */
    private void appendTree(Path directory, int maxDepth, int level, StringBuilder output,
                            ToolExecutionContext context) throws IOException {
        if (level >= maxDepth || output.length() >= MAX_OUTPUT) {
            return;
        }
        // 排序后的子路径。
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                children.add(child);
                if (children.size() > MAX_SEARCH_FILES) {
                    throw new IllegalArgumentException("单级目录条目数超过上限");
                }
            }
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path child : children) {
            context.checkActive();
            if (output.length() >= MAX_OUTPUT) {
                break;
            }
            // 是否为不允许遍历的符号链接。
            boolean link = Files.isSymbolicLink(child);
            output.append("  ".repeat(level)).append(child.getFileName());
            output.append(link ? " [symlink]\n" : Files.isDirectory(child) ? "/\n" : "\n");
            if (!link && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                appendTree(child, maxDepth, level + 1, output, context);
            }
        }
    }

    /**
     * 读取指定闭区间的行及整文件哈希。
     *
     * @param args 文件与行范围
     * @param context 执行上下文
     * @return 带行号与哈希的文件内容
     * @throws IOException 读取失败时
     */
    private ToolExecutionResult view(FileViewArgs args, ToolExecutionContext context) throws IOException {
        // 已确认的普通文件。
        Path file = regular(args.path);
        // 文件原始字节。
        byte[] bytes = readBytes(file);
        // 文件行列表。
        List<String> lines = lines(decode(bytes));
        // 起始行号。
        int start = args.startLine == null ? 1 : args.startLine;
        // 末尾行号。
        int end = args.endLine == null ? lines.size() : args.endLine;
        if (start < 1 || end < start - 1 || start > lines.size() + 1) {
            throw new IllegalArgumentException("行范围无效");
        }
        // 文件版本和内容输出。
        StringBuilder output = new StringBuilder("sha256=").append(HashUtils.sha256(bytes))
                .append(" lines=").append(lines.size()).append('\n');
        for (int index = start; index <= Math.min(end, lines.size()); index++) {
            context.checkActive();
            output.append(index).append(": ").append(lines.get(index - 1)).append('\n');
            if (output.length() >= MAX_OUTPUT) {
                break;
            }
        }
        return bounded(output.toString());
    }

    /**
     * 搜索文件名中的关键词。
     *
     * @param args 路径与关键词
     * @param context 执行上下文
     * @return 文件路径列表
     * @throws IOException 搜索失败时
     */
    private ToolExecutionResult searchFiles(FileSearchArgs args, ToolExecutionContext context) throws IOException {
        // 搜索根目录。
        Path directory = directory(args.path);
        // 非空关键词集合。
        List<String> keywords = keywords(args.keywords);
        // 命中的相对路径。
        StringBuilder output = new StringBuilder();
        try (Stream<Path> stream = Files.walk(directory)) {
            // 待检查路径的有界列表。
            List<Path> paths = stream.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                    .limit(MAX_SEARCH_FILES + 1L).sorted().toList();
            if (paths.size() > MAX_SEARCH_FILES) {
                throw new IllegalArgumentException("搜索文件数超过上限");
            }
            for (Path file : paths) {
                context.checkActive();
                if (matches(file.getFileName().toString(), keywords)) {
                    output.append(policy.root().relativize(file)).append('\n');
                }
                if (output.length() >= MAX_OUTPUT) {
                    break;
                }
            }
        }
        return bounded(output.length() == 0 ? "(无匹配文件)" : output.toString());
    }

    /**
     * 在单文件中搜索文本并返回上下文。
     *
     * @param args 搜索参数
     * @param context 执行上下文
     * @return 匹配行及上下文
     * @throws IOException 读取失败时
     */
    private ToolExecutionResult searchInFile(FileSearchInFileArgs args, ToolExecutionContext context) throws IOException {
        return searchContent(regular(args.path), keywords(args.keywords != null ? args.keywords : args.patterns),
                args.contextLines, context);
    }

    /**
     * 在目录中的普通文件搜索文本。
     *
     * @param args 搜索参数
     * @param context 执行上下文
     * @return 带文件名的匹配行
     * @throws IOException 搜索失败时
     */
    private ToolExecutionResult searchInDirectory(DirectorySearchArgs args, ToolExecutionContext context)
            throws IOException {
        // 搜索目录。
        Path directory = directory(args.path);
        // 关键词集合。
        List<String> terms = keywords(args.keywords != null ? args.keywords : args.patterns);
        // 聚合输出。
        StringBuilder output = new StringBuilder();
        try (Stream<Path> stream = Files.walk(directory)) {
            // 有界的普通文件集合。
            List<Path> paths = stream.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                    .limit(MAX_SEARCH_FILES + 1L).sorted().toList();
            if (paths.size() > MAX_SEARCH_FILES) {
                throw new IllegalArgumentException("搜索文件数超过上限");
            }
            for (Path file : paths) {
                context.checkActive();
                if (Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                try {
                    // 单文件匹配结果。
                    String found = searchContent(file, terms, args.contextLines, context).getContent();
                    if (!found.equals("(无匹配内容)")) {
                        output.append(policy.root().relativize(file)).append('\n').append(found);
                    }
                } catch (IllegalArgumentException exception) {
                    if (!"文件不是有效 UTF-8".equals(exception.getMessage())) {
                        throw exception;
                    }
                }
                if (output.length() >= MAX_OUTPUT) {
                    break;
                }
            }
        }
        return bounded(output.length() == 0 ? "(无匹配内容)" : output.toString());
    }

    /**
     * 搜索一个文件中的多关键词并合并重叠上下文。
     *
     * @param file 文件路径
     * @param terms 关键词
     * @param contextLines 上下文行数
     * @param context 执行上下文
     * @return 匹配结果
     * @throws IOException 读取失败时
     */
    private ToolExecutionResult searchContent(Path file, List<String> terms, Integer contextLines,
                                               ToolExecutionContext context) throws IOException {
        // 上下文行数。
        int surrounding = contextLines == null ? 0 : contextLines;
        if (surrounding < 0 || surrounding > 20) {
            throw new IllegalArgumentException("contextLines 必须在 0 到 20 之间");
        }
        // 文件内容行。
        List<String> lines = lines(decode(readBytes(file)));
        // 需输出的行标记。
        boolean[] selected = new boolean[lines.size()];
        for (int index = 0; index < lines.size(); index++) {
            context.checkActive();
            if (matches(lines.get(index), terms)) {
                Arrays.fill(selected, Math.max(0, index - surrounding),
                        Math.min(lines.size(), index + surrounding + 1), true);
            }
        }
        // 有行号的匹配文本。
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (selected[index]) {
                output.append(index + 1).append(": ").append(lines.get(index)).append('\n');
            }
            if (output.length() >= MAX_OUTPUT) {
                break;
            }
        }
        return bounded(output.length() == 0 ? "(无匹配内容)" : output.toString());
    }

    /**
     * 创建新文件，允许创建父目录但不覆盖已有路径。
     *
     * @param args 创建参数
     * @param context 执行上下文
     * @return 新文件摘要
     * @throws IOException 创建失败时
     */
    private ToolExecutionResult create(FileCreateArgs args, ToolExecutionContext context) throws IOException {
        // 目标文件。
        Path file = policy.resolve(args.path);
        if (file.equals(policy.root()) || args.content == null) {
            throw new IllegalArgumentException("文件路径和内容不能为空");
        }
        // 待写入的 UTF-8 字节。
        byte[] bytes = encode(args.content);
        checkSize(bytes);
        context.checkActive();
        Files.createDirectories(file.getParent());
        policy.resolve(args.path);
        Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        record(context, "CREATE", args.path, HashUtils.sha256(bytes));
        return ToolExecutionResult.text("created=" + args.path + " sha256=" + HashUtils.sha256(bytes));
    }

    /**
     * 校验版本后替换、插入或追加文件内容。
     *
     * @param args 编辑参数
     * @param context 执行上下文
     * @return 变更摘要
     * @throws IOException 编辑失败时
     */
    private ToolExecutionResult edit(FileEditArgs args, ToolExecutionContext context) throws IOException {
        // 目标普通文件。
        Path file = regular(args.path);
        // 当前文件字节。
        byte[] before = readBytes(file);
        requireHash(args.expectedHash, before);
        if (args.mode == null || args.content == null) {
            throw new IllegalArgumentException("mode 和 content 必填");
        }
        // 当前 UTF-8 文本。
        String original = decode(before);
        // 修改后的文本。
        String updated;
        switch (args.mode.toLowerCase(Locale.ROOT)) {
            case "replace" -> {
                if (args.startLine != null || args.endLine != null) {
                    // 当前行数。
                    int count = lines(original).size();
                    if (args.startLine == null || args.endLine == null || args.startLine < 1
                            || args.endLine < args.startLine || args.endLine > count) {
                        throw new IllegalArgumentException("替换行范围无效");
                    }
                    // 待替换范围起始偏移。
                    int start = lineOffset(original, args.startLine);
                    // 待替换范围末尾偏移。
                    int end = lineOffset(original, args.endLine + 1);
                    updated = original.substring(0, start) + args.content + original.substring(end);
                } else {
                    if (args.oldText == null || args.oldText.isEmpty()) {
                        throw new IllegalArgumentException("replace 需要行范围或非空 oldText");
                    }
                    // 首次匹配位置。
                    int first = original.indexOf(args.oldText);
                    if (first < 0 || original.indexOf(args.oldText, first + 1) >= 0) {
                        throw new IllegalArgumentException("oldText 必须恰好匹配一次");
                    }
                    updated = original.substring(0, first) + args.content
                            + original.substring(first + args.oldText.length());
                }
            }
            case "insert" -> {
                // 当前行数。
                List<String> currentLines = lines(original);
                // 兼容参考工具的插入行号。
                Integer line = args.line != null ? args.line : args.startLine;
                if (line == null || line < 1 || line > currentLines.size() + 1) {
                    throw new IllegalArgumentException("插入行超出边界");
                }
                // 插入位置的字符偏移。
                int offset = lineOffset(original, line);
                updated = original.substring(0, offset) + args.content + original.substring(offset);
            }
            case "append" -> updated = original + args.content;
            default -> throw new IllegalArgumentException("不支持的编辑模式");
        }
        // 新版本字节。
        byte[] after = encode(updated);
        checkSize(after);
        context.checkActive();
        requireHash(args.expectedHash, readBytes(file));
        // 同目录中的临时文件。
        Path temp = Files.createTempFile(file.getParent(), ".codegen-", ".tmp");
        try {
            Files.write(temp, after);
            context.checkActive();
            requireHash(args.expectedHash, readBytes(file));
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        record(context, "EDIT", args.path, HashUtils.sha256(after));
        return ToolExecutionResult.text("edited=" + args.path + " before=" + HashUtils.sha256(before)
                + " after=" + HashUtils.sha256(after));
    }

    /**
     * 校验源版本并将文件移动到未占用的新路径。
     *
     * @param args 移动参数
     * @param context 执行上下文
     * @return 移动摘要
     * @throws IOException 移动失败时
     */
    private ToolExecutionResult move(FileMoveArgs args, ToolExecutionContext context) throws IOException {
        // 已校验的源文件。
        Path source = regular(args.path != null ? args.path : args.source);
        // 已校验的目标路径。
        Path target = policy.resolve(args.targetPath != null ? args.targetPath : args.target);
        if (source.equals(target) || target.equals(policy.root()) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("目标路径已存在或无效");
        }
        // 源文件版本。
        byte[] before = readBytes(source);
        requireHash(args.expectedHash, before);
        context.checkActive();
        Files.createDirectories(target.getParent());
        policy.resolve(args.targetPath);
        requireHash(args.expectedHash, readBytes(source));
        Files.move(source, target);
        // 实际源文件相对路径。
        String sourceName = args.path != null ? args.path : args.source;
        // 实际目标文件相对路径。
        String targetName = args.targetPath != null ? args.targetPath : args.target;
        record(context, "MOVE", targetName, HashUtils.sha256(before));
        return ToolExecutionResult.text("moved=" + sourceName + " -> " + targetName
                + " sha256=" + HashUtils.sha256(before));
    }

    /**
     * 校验版本后删除普通文件，目录和缺失文件均拒绝。
     *
     * @param args 删除参数
     * @param context 执行上下文
     * @return 删除摘要
     * @throws IOException 删除失败时
     */
    private ToolExecutionResult delete(FileDeleteArgs args, ToolExecutionContext context) throws IOException {
        // 目标普通文件。
        Path file = regular(args.path);
        // 删除前版本。
        byte[] before = readBytes(file);
        requireHash(args.expectedHash, before);
        context.checkActive();
        requireHash(args.expectedHash, readBytes(file));
        Files.delete(file);
        record(context, "DELETE", args.path, HashUtils.sha256(before));
        return ToolExecutionResult.text("deleted=" + args.path + " sha256=" + HashUtils.sha256(before));
    }

    /**
     * 检查目录路径。
     *
     * @param relative 相对路径
     * @return 目录路径
     */
    private Path directory(String relative) {
        // 已校验路径。
        Path path = policy.resolve(relative == null ? "" : relative);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("目录不存在");
        }
        return path;
    }

    /**
     * 检查普通文件路径。
     *
     * @param relative 相对路径
     * @return 普通文件路径
     */
    private Path regular(String relative) {
        // 已校验路径。
        Path path = policy.resolve(relative);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("普通文件不存在");
        }
        return path;
    }

    /**
     * 在大小限制内读取完整文件。
     *
     * @param file 文件路径
     * @return 文件字节
     * @throws IOException 读取失败时
     */
    private static byte[] readBytes(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件超过读取上限");
        }
        // 完整文件字节。
        byte[] bytes = Files.readAllBytes(file);
        checkSize(bytes);
        return bytes;
    }

    /**
     * 严格解码 UTF-8。
     *
     * @param bytes 原始字节
     * @return 文本
     */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("文件不是有效 UTF-8", exception);
        }
    }

    /**
     * 编码文本为 UTF-8。
     *
     * @param text 文本
     * @return 字节
     */
    private static byte[] encode(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 检查文件大小。
     *
     * @param bytes 文件字节
     */
    private static void checkSize(byte[] bytes) {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件超过 1 MiB 上限");
        }
    }

    /**
     * 校验读取时的文件版本。
     *
     * @param expected 模型提交的哈希
     * @param bytes 当前文件字节
     */
    private static void requireHash(String expected, byte[] bytes) {
        if (expected == null || !expected.matches("[0-9a-f]{64}")
                || !expected.equals(HashUtils.sha256(bytes))) {
            throw new IllegalArgumentException("文件版本冲突：请重新读取 sha256");
        }
    }

    /**
     * 按不同换行符划分文件行，不将末尾换行计为额外空行。
     *
     * @param content 文件内容
     * @return 文件行列表
     */
    private static List<String> lines(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        // 保留内部空行的拆分结果。
        String[] parts = content.split("\\r\\n|\\n|\\r", -1);
        // 有效行数。
        int count = parts.length - (content.endsWith("\n") || content.endsWith("\r") ? 1 : 0);
        return Arrays.asList(parts).subList(0, count);
    }

    /**
     * 计算插入行的字符偏移，保留原始换行风格。
     *
     * @param content 原文
     * @param line 目标行号
     * @return 字符偏移
     */
    private static int lineOffset(String content, int line) {
        if (line == 1) {
            return 0;
        }
        // 已经过的换行次数。
        int passed = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n' || (content.charAt(index) == '\r'
                    && (index + 1 == content.length() || content.charAt(index + 1) != '\n'))) {
                passed++;
                if (passed == line - 1) {
                    return index + 1;
                }
            }
        }
        return content.length();
    }

    /**
     * 校验多个非空关键词。
     *
     * @param values 关键词
     * @return 关键词快照
     */
    private static List<String> keywords(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 20
                || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("keywords 需要 1 到 20 个非空关键词");
        }
        return List.copyOf(values);
    }

    /**
     * 判断文本是否包含任一关键词，忽略大小写。
     *
     * @param text 待搜索文本
     * @param terms 关键词
     * @return 是否匹配
     */
    private static boolean matches(String text, List<String> terms) {
        // 小写比较文本。
        String normalized = text.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(term -> normalized.contains(term.toLowerCase(Locale.ROOT)));
    }

    /**
     * 限制工具输出字符数。
     *
     * @param content 完整文本
     * @return 带截断标记的结果
     */
    private static ToolExecutionResult bounded(String content) {
        return content.length() <= MAX_OUTPUT ? ToolExecutionResult.text(content)
                : new ToolExecutionResult(content.substring(0, MAX_OUTPUT), true);
    }










}
