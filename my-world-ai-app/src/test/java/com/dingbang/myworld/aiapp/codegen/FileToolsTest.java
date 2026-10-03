package com.dingbang.myworld.aiapp.codegen;

import com.dingbang.myworld.agent.tool.ToolExecutionContext;
import com.dingbang.myworld.agent.tool.ToolExecutor;
import com.dingbang.myworld.agent.tool.ToolRegistry;
import com.dingbang.myworld.aiapp.codegen.tool.FileTools;
import com.dingbang.myworld.aiapp.codegen.tool.WorkspacePolicy;
import com.dingbang.myworld.aiframework.model.ToolCall;
import com.dingbang.myworld.aiframework.model.ToolResult;
import com.dingbang.myworld.aiframework.model.ToolResultStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证文件工具的路径边界、版本冲突和读写行为。
 *
 * @author Sebastian
 * @since 2026/10/03
 */
class FileToolsTest {
    /** 测试用临时工作目录。 */
    @TempDir
    Path workspace;

    /**
     * 验证读取行号、搜索上下文及版本化编辑。
     *
     * @throws Exception 文件操作失败时
     */
    @Test
    void readsSearchesAndEditsWithVersion() throws Exception {
        /** 测试文件。 */
        Path file = workspace.resolve("src/demo.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "alpha\r\nbeta\r\ngamma\r\n");
        /** 文件工具执行器。 */
        ToolExecutor executor = executor();
        /** 读取结果。 */
        ToolResult view = invoke(executor, "view_file", """ 
                {"path":"src/demo.txt","startLine":2,"endLine":3}
                """);
        assertThat(view.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(view.getContent()).contains("2: beta", "3: gamma", "lines=3");
        /** 当前版本。 */
        String version = hash(view.getContent());
        /** 多词搜索结果。 */
        ToolResult found = invoke(executor, "search_in_file", """
                {"path":"src/demo.txt","keywords":["beta","other"],"contextLines":1}
                """);
        assertThat(found.getContent()).contains("1: alpha", "2: beta", "3: gamma");
        /** 插入后的工具结果。 */
        ToolResult edited = invoke(executor, "edit_file", """
                {"path":"src/demo.txt","expectedHash":"%s","mode":"INSERT","content":"new\\r\\n","line":2}
                """.formatted(version));
        assertThat(edited.getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(Files.readString(file)).isEqualTo("alpha\r\nnew\r\nbeta\r\ngamma\r\n");
        assertThat(invoke(executor, "edit_file", """
                {"path":"src/demo.txt","expectedHash":"%s","mode":"APPEND","content":"late"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(Files.readString(file)).doesNotContain("late");
        /** 插入后重新读取的版本。 */
        String updatedVersion = hash(invoke(executor, "view_file", """
                {"path":"src/demo.txt"}
                """).getContent());
        assertThat(invoke(executor, "edit_file", """
                {"path":"src/demo.txt","expectedHash":"%s","mode":"replace","startLine":2,"endLine":3,"content":"replaced\\r\\n"}
                """.formatted(updatedVersion)).getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(Files.readString(file)).isEqualTo("alpha\r\nreplaced\r\ngamma\r\n");
    }

    /**
     * 验证创建、移动、删除拒绝覆盖和重复删除。
     *
     * @throws Exception 文件操作失败时
     */
    @Test
    void createsMovesAndDeletesWithoutOverwrite() throws Exception {
        /** 文件工具执行器。 */
        ToolExecutor executor = executor();
        assertThat(invoke(executor, "create_file", """
                {"path":"new/a.txt","content":"hello"}
                """).getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(invoke(executor, "create_file", """
                {"path":"new/empty.txt","content":""}
                """).getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(invoke(executor, "view_file", """
                {"path":"new/empty.txt"}
                """).getContent()).contains("lines=0");
        assertThat(invoke(executor, "create_file", """
                {"path":"new/a.txt","content":"overwrite"}
                """).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        /** 已创建的原文。 */
        String version = hash(invoke(executor, "view_file", """
                {"path":"new/a.txt"}
                """).getContent());
        assertThat(invoke(executor, "move_file", """
                {"path":"new/a.txt","targetPath":"other/b.txt","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(Files.exists(workspace.resolve("new/a.txt"))).isFalse();
        assertThat(invoke(executor, "delete_file", """
                {"path":"other/b.txt","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(invoke(executor, "delete_file", """
                {"path":"other/b.txt","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
    }

    /**
     * 验证越界路径、符号链接和陈旧版本不会产生副作用。
     *
     * @throws Exception 文件操作失败时
     */
    @Test
    void rejectsEscapesLinksAndConcurrentChanges() throws Exception {
        /** 文件工具执行器。 */
        ToolExecutor executor = executor();
        assertThat(invoke(executor, "create_file", """
                {"path":"../escape.txt","content":"bad"}
                """).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        /** 指向工作目录外的符号链接。 */
        Path link = workspace.resolve("linked");
        Files.createSymbolicLink(link, workspace.getParent());
        assertThat(invoke(executor, "create_file", """
                {"path":"linked/escape.txt","content":"bad"}
                """).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        /** 被并发改动的文件。 */
        Path file = workspace.resolve("fresh.txt");
        Files.writeString(file, "old", StandardCharsets.UTF_8);
        /** 读取时的版本。 */
        String version = hash(invoke(executor, "view_file", """
                {"path":"fresh.txt"}
                """).getContent());
        Files.writeString(file, "new", StandardCharsets.UTF_8);
        assertThat(invoke(executor, "delete_file", """
                {"path":"fresh.txt","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(Files.readString(file)).isEqualTo("new");
    }

    /**
     * 验证空目录、目录深度、大文件输出截断和无效编码。
     *
     * @throws Exception 文件操作失败时
     */
    @Test
    void boundsDirectoryAndFileReads() throws Exception {
        /** 文件工具执行器。 */
        ToolExecutor executor = executor();
        assertThat(invoke(executor, "list_directory_tree", """
                {"path":"","depth":3}
                """).getContent()).contains("空目录");
        /** 嵌套目录。 */
        Path nested = workspace.resolve("a/b");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("deep.txt"), "deep");
        assertThat(invoke(executor, "list_directory_tree", """
                {"path":"","maxDepth":1}
                """).getContent()).contains("a/").doesNotContain("deep.txt");
        assertThat(invoke(executor, "list_directory_tree", """
                {"path":"","depth":3}
                """).getContent()).contains("deep.txt");
        /** 超过输出长度但在读取上限内的文本。 */
        Files.writeString(workspace.resolve("large.txt"), "x".repeat(20000));
        assertThat(invoke(executor, "view_file", """
                {"path":"large.txt"}
                """).isTruncated()).isTrue();
        /** 无效 UTF-8 字节。 */
        Files.write(workspace.resolve("binary.dat"), new byte[] {(byte) 0xff});
        assertThat(invoke(executor, "view_file", """
                {"path":"binary.dat"}
                """).getStatus()).isEqualTo(ToolResultStatus.ERROR);
    }

    /**
     * 验证目录搜索、多关键词和移动两端的边界。
     *
     * @throws Exception 文件操作失败时
     */
    @Test
    void searchesDirectoryAndRejectsMoveConflicts() throws Exception {
        /** 文件工具执行器。 */
        ToolExecutor executor = executor();
        Files.writeString(workspace.resolve("one.txt"), "first\nneedle\nlast\n");
        Files.writeString(workspace.resolve("two.java"), "other\n");
        assertThat(invoke(executor, "search_files", """
                {"path":"","keywords":["java","missing"]}
                """).getContent()).contains("two.java").doesNotContain("one.txt");
        assertThat(invoke(executor, "search_in_directory", """
                {"path":"","patterns":["needle","absent"],"contextLines":1}
                """).getContent()).contains("one.txt", "1: first", "2: needle", "3: last");
        /** 源文件版本。 */
        String version = hash(invoke(executor, "view_file", """
                {"path":"one.txt"}
                """).getContent());
        assertThat(invoke(executor, "move_file", """
                {"path":"one.txt","targetPath":"two.java","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(invoke(executor, "move_file", """
                {"path":"one.txt","targetPath":"../outside.txt","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(invoke(executor, "delete_file", """
                {"path":"","expectedHash":"%s"}
                """.formatted(version)).getStatus()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(Files.readString(workspace.resolve("one.txt"))).contains("needle");
    }

    /**
     * 创建只针对临时目录的工具执行器。
     *
     * @return 工具执行器
     */
    private ToolExecutor executor() {
        return new ToolExecutor(new ToolRegistry(new FileTools(new WorkspacePolicy(workspace)).all()));
    }

    /**
     * 调用工具并取得结构化结果。
     *
     * @param executor 执行器
     * @param name 工具名称
     * @param json 参数 JSON
     * @return 工具结果
     */
    private ToolResult invoke(ToolExecutor executor, String name, String json) {
        return executor.execute(new ToolCall("test-call", name, json),
                new ToolExecutionContext("test-run", "test-session", workspace, null), null);
    }

    /**
     * 从读取结果提取文件 SHA-256。
     *
     * @param content 读取工具输出
     * @return 文件哈希
     */
    private String hash(String content) {
        /** 哈希匹配器。 */
        Matcher matcher = Pattern.compile("sha256=([0-9a-f]{64})").matcher(content);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
