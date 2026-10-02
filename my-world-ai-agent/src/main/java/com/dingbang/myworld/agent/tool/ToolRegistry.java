package com.dingbang.myworld.agent.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 冻结已校验工具的名称索引与模型可见描述。
 *
 * @author Sebastian
 * @since 2026/10/02
 */
public final class ToolRegistry {
    /**
     * 按工具名称索引的可信工具。
     */
    private final Map<String, Tool<?>> tools;
    /**
     * 按工具名称索引的描述。
     */
    private final Map<String, ToolDescriptor<?>> descriptors;

    /**
     * 校验并冻结工具集合；同一实例重复注册时去重。
     *
     * @param tools 待注册工具
     * @throws IllegalArgumentException 同名对应不同实例时
     */
    public ToolRegistry(Collection<? extends Tool<?>> tools) {
        Objects.requireNonNull(tools, "工具集合不能为 null");
        // 待冻结的工具名称索引。
        Map<String, Tool<?>> registered = new LinkedHashMap<>();
        // 待冻结的描述名称索引。
        Map<String, ToolDescriptor<?>> described = new LinkedHashMap<>();
        for (Tool<?> tool : tools) {
            Objects.requireNonNull(tool, "工具不能为 null");
            // 当前工具提供的可信描述。
            ToolDescriptor<?> descriptor = Objects.requireNonNull(tool.descriptor(), "工具描述不能为 null");
            if (!descriptor.getParameterType().equals(tool.parameterType())) {
                throw new IllegalArgumentException("工具描述与实现的参数类型不一致: " + descriptor.getName());
            }
            // 同名已有工具。
            Tool<?> prior = registered.putIfAbsent(descriptor.getName(), tool);
            if (prior != null && prior != tool) {
                throw new IllegalArgumentException("工具名称冲突: " + descriptor.getName());
            }
            described.putIfAbsent(descriptor.getName(), descriptor);
        }
        this.tools = Collections.unmodifiableMap(registered);
        this.descriptors = Collections.unmodifiableMap(described);
    }

    /**
     * 保留已校验描述，不再次调用可能动态变化的工具描述方法。
     *
     * @param tools 已授权工具索引
     * @param descriptors 对应的已校验描述索引
     */
    private ToolRegistry(Map<String, Tool<?>> tools, Map<String, ToolDescriptor<?>> descriptors) {
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        this.descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(descriptors));
    }

    /**
     * 返回按名称注册的工具，仅供可信执行器使用。
     *
     * @param name 工具名称
     * @return 工具，未注册时为 null
     */
    Tool<?> findTool(String name) {
        return tools.get(name);
    }

    /**
     * 返回按名称注册的描述。
     *
     * @param name 工具名称
     * @return 描述，未注册时为 null
     */
    ToolDescriptor<?> findDescriptor(String name) {
        return descriptors.get(name);
    }

    /**
     * 返回工具描述的不可修改快照。
     *
     * @return 工具描述列表
     */
    public List<ToolDescriptor<?>> getDescriptors() {
        return Collections.unmodifiableList(new ArrayList<>(descriptors.values()));
    }

    /**
     * 按受信任的工具名称集合创建运行级授权快照。
     *
     * @param names 已授权工具名称
     * @return 只含授权工具的注册表
     * @throws IllegalArgumentException 包含未知工具名称时
     */
    public ToolRegistry select(Collection<String> names) {
        Objects.requireNonNull(names, "工具名称集合不能为 null");
        // 本次运行实际授权的工具索引。
        Map<String, Tool<?>> selected = new LinkedHashMap<>();
        // 对应的已冻结参数描述。
        Map<String, ToolDescriptor<?>> selectedDescriptors = new LinkedHashMap<>();
        for (String name : names) {
            // 按可信名称找到的工具。
            Tool<?> tool = tools.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("未注册工具: " + name);
            }
            selected.putIfAbsent(name, tool);
            selectedDescriptors.putIfAbsent(name, descriptors.get(name));
        }
        return new ToolRegistry(selected, selectedDescriptors);
    }
}
