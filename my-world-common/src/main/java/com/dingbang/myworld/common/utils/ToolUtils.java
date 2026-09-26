package com.dingbang.myworld.common.utils;

import io.vavr.control.Try;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 通用工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class ToolUtils {

    /**
     * 按条件循环
     *
     * @param function
     * @param endCondition
     * @param times
     * @param <T>
     * @return
     */
    public static <T> T loopWith(Function<Integer, T> function, Predicate<T> endCondition, int times) {
        return loopSleepWith(function, endCondition, times, null);
    }

    /**
     * 按条件循环 - 带休眠间隔
     *
     * @param function
     * @param endCondition
     * @param times
     * @param intervalSeconds
     * @param <T>
     * @return
     */
    public static <T> T loopSleepWith(Function<Integer, T> function, Predicate<T> endCondition, int times, Integer intervalSeconds) {
        T t = null;
        int current = 1;

        do {
            t = function.apply(current);
            if (Objects.nonNull(endCondition) && endCondition.test(t)) {
                return t;
            }

            if (Objects.nonNull(intervalSeconds)) {
                Try.run(() -> {
                    TimeUnit.SECONDS.sleep(intervalSeconds);
                });
            }
        } while (current++ < times);

        return t;
    }

}
