package com.dingbang.myworld.aiapp.codegen.tool;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 JDK 语法树定位 Java 类型、方法、构造器和字段声明，不编译或执行用户代码。
 *
 * @author Sebastian
 * @since 2026/10/05
 */
final class JavaSymbolLocator {
    /**
     * 查找名称完全匹配的声明，重载方法保留全部候选。
     *
     * @param source 已读取的 Java 源文件
     * @param symbol 简单名称或 Outer.Inner#member 格式的限定名称
     * @return 声明的绝对行号范围
     * @throws IOException 语法解析失败时
     * @throws IllegalArgumentException 源码语法错误或运行环境缺少 JDK 编译器时
     */
    static List<SymbolRange> locate(String source, String symbol) throws IOException {
        /** 当前 JDK 的编译器；仅调用解析阶段。 */
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalArgumentException("符号读取需要完整 JDK；请改用 startLine/endLine");
        }
        /** 隔离编译器诊断，避免向日志打印用户源码。 */
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        /** 内存源文件，避免编译器再次读取磁盘导致哈希与内容不一致。 */
        var input = new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
            /**
             * 返回待解析的源文件快照。
             *
             * @param ignoreEncodingErrors 是否忽略编码错误，此处内容已经完成解码
             * @return 源文件快照
             */
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        /** 命中声明，保持源码顺序。 */
        List<SymbolRange> matches = new ArrayList<>();
        /** 本次解析专用文件管理器，结束时释放资源。 */
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            /** 禁用注解处理且不调用 analyze/generate，不需要项目依赖。 */
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none"), null, List.of(input));
            /** 解析后的编译单元。 */
            for (CompilationUnitTree unit : task.parse()) {
                /** 源码字符偏移查询器。 */
                SourcePositions positions = Trees.instance(task).getSourcePositions();
                new TreePathScanner<Void, Void>() {
                    /**
                     * 收集类型声明并访问其成员。
                     *
                     * @param node 类型声明
                     * @param unused 未使用的扫描参数
                     * @return 空值
                     */
                    @Override
                    public Void visitClass(ClassTree node, Void unused) {
                        add(node, node.getSimpleName().toString(), owner(getCurrentPath()));
                        return super.visitClass(node, unused);
                    }

                    /**
                     * 收集方法和构造器声明。
                     *
                     * @param node 方法声明
                     * @param unused 未使用的扫描参数
                     * @return 空值
                     */
                    @Override
                    public Void visitMethod(MethodTree node, Void unused) {
                        /** 所属类型的限定名称。 */
                        String owner = owner(getCurrentPath().getParentPath());
                        /** 构造器使用类型名称匹配。 */
                        String name = node.getName().contentEquals("<init>")
                                ? owner.substring(owner.lastIndexOf('.') + 1) : node.getName().toString();
                        add(node, name, owner + "#" + name);
                        return super.visitMethod(node, unused);
                    }

                    /**
                     * 只收集类型成员字段，不将方法参数或局部变量误认为字段。
                     *
                     * @param node 变量声明
                     * @param unused 未使用的扫描参数
                     * @return 空值
                     */
                    @Override
                    public Void visitVariable(VariableTree node, Void unused) {
                        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree) {
                            add(node, node.getName().toString(),
                                    owner(getCurrentPath().getParentPath()) + "#" + node.getName());
                        }
                        return super.visitVariable(node, unused);
                    }

                    /**
                     * 将匹配声明的字符边界转换为绝对行号。
                     *
                     * @param node 声明节点
                     * @param name 简单名称
                     * @param qualified 限定名称
                     */
                    private void add(Tree node, String name, String qualified) {
                        if (name.isEmpty() || (!symbol.equals(name) && !symbol.equals(qualified))) return;
                        /** 声明起始字符偏移。 */
                        long start = positions.getStartPosition(unit, node);
                        /** 声明结束字符偏移，不含该位置。 */
                        long end = positions.getEndPosition(unit, node);
                        if (start < 0 || end <= start) return;
                        matches.add(new SymbolRange(qualified, node.getKind().name(),
                                (int) unit.getLineMap().getLineNumber(start),
                                (int) unit.getLineMap().getLineNumber(end - 1)));
                    }
                }.scan(unit, null);
            }
            if (diagnostics.getDiagnostics().stream().anyMatch(item -> item.getKind() == Diagnostic.Kind.ERROR)) {
                throw new IllegalArgumentException("Java 语法解析失败；请改用 startLine/endLine 或 search_in_file");
            }
        }
        return List.copyOf(matches);
    }

    /**
     * 获取声明所处的嵌套类型名称。
     *
     * @param path 当前节点路径
     * @return Outer.Inner 格式的名称
     */
    private static String owner(TreePath path) {
        /** 从外部类型到内部类型的名称。 */
        List<String> names = new ArrayList<>();
        /** 当前向上遍历的节点。 */
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            if (current.getLeaf() instanceof ClassTree) {
                names.add(0, ((ClassTree) current.getLeaf()).getSimpleName().toString());
            }
        }
        return String.join(".", names);
    }

    /**
     * 保存一个声明的限定名称、语法类型和闭区间行号。
     *
     * @param name 限定名称
     * @param kind 语法节点类型
     * @param start 起始行号
     * @param end 结束行号
     * @author Sebastian
     * @since 2026/10/05
     */
    record SymbolRange(String name, String kind, int start, int end) { }
}
