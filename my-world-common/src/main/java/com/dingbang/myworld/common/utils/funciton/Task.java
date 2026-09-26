package com.dingbang.myworld.common.utils.funciton;

import com.dingbang.myworld.common.utils.lang.ObjectUtils;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 可序列化任务接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@FunctionalInterface
public interface Task extends Runnable, Serializable {

    /**
     * 执行任务
     */
    void execute();

    /**
     * run task
     */
    @Override
    default void run() {
        execute();
    }

    /**
     * 不处理
     */
    Task NONE = () -> {};

    /**
     * 根据入参转换
     * @param param1 参数1
     * @param param2 参数2
     * @param valueMapping
     * @return
     * @param <P1>
     * @param <P2>
     */
    static <P1, P2> Task mapping(P1 param1, P2 param2, BiFunction<P1, P2, Task> valueMapping) {
        if (ObjectUtils.hasEmpty(param1, param2)) {
            return NONE;
        }

        return valueMapping.apply(param1, param2);
    }

    /**
     * 根据入参转换
     * @param param1 参数1
     * @param valueMapping
     * @return
     * @param <P1>
     */
    static <P1> Task mapping(P1 param1, Function<P1, Task> valueMapping) {
        if (ObjectUtils.isEmpty(param1)) {
            return NONE;
        }

        return valueMapping.apply(param1);
    }

}
