package com.dingbang.myworld.common.utils.time;

import com.google.common.collect.Lists;
import com.dingbang.myworld.common.utils.CopierUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 时间区间处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class TimeIntervalUtils {

    /**
     * 是否有交集
     * @param timeInterval1 时间段1
     * @param timeInterval2 时间段2
     * @return 是否有交集
     */
    public static boolean hasIntersection(TimeInterval timeInterval1, TimeInterval timeInterval2) {
        return !(timeInterval1.getEndTime().isBefore(timeInterval2.getStartTime()) || timeInterval2.getEndTime().isBefore(timeInterval1.getStartTime()));
    }

    /**
     * 合并多个区间段
     *
     * @param timeIntervals 区间段
     * @return 合并结果
     */
    public static List<TimeInterval> mergeTimeIntervals(List<TimeInterval> timeIntervals) {
        // 先按照开始时间升序排序
        timeIntervals.sort((o1, o2) -> {
            if (o1.getStartTime().isBefore(o2.getStartTime())) {
                return -1;
            } else if (o1.getStartTime().equals(o2.getStartTime())) {
                return 0;
            } else {
                return 1;
            }
        });
        List<TimeInterval> mergeTimeInterval = Lists.newArrayList();
        // 依次合并时间段
        for (TimeInterval timeInterval : timeIntervals) {
            if (mergeTimeInterval.size() == 0) {
                mergeTimeInterval.add(timeInterval);
                continue;
            }
            TimeInterval lastTimeInterval = mergeTimeInterval.get(mergeTimeInterval.size() - 1);
            if (timeInterval.getStartTime().isBefore(lastTimeInterval.getEndTime())) {
                if (timeInterval.getEndTime().isAfter(lastTimeInterval.getEndTime())) {
                    lastTimeInterval.setEndTime(timeInterval.getEndTime());
                } else {
                    continue;
                }
            } else {
                mergeTimeInterval.add(timeInterval);
            }
        }
        return mergeTimeInterval;
    }

    /**
     * 将originTimeInterval减去minusTimeInterval时间段后剩下的时间段
     *
     * @param originTimeInterval 原始时间段
     * @param minusTimeIntervals 待减去的时间段
     * @return 结果时间段
     */
    public static List<TimeInterval> minus(TimeInterval originTimeInterval, List<TimeInterval> minusTimeIntervals) {
        if (CollectionUtils.isEmpty(minusTimeIntervals)) {
            return Lists.newArrayList(originTimeInterval);
        }
        TimeInterval originTimeIntervalCopy = CopierUtils.copyProperties(originTimeInterval, TimeInterval.class);
        List<TimeInterval> minusTimeIntervalsCopy = CopierUtils.copyObjects(minusTimeIntervals, TimeInterval.class);
        return doMinus(originTimeIntervalCopy, minusTimeIntervalsCopy);
    }

    private static List<TimeInterval> doMinus(TimeInterval originTimeInterval, List<TimeInterval> minusTimeIntervals) {
        List<TimeInterval> result = Lists.newArrayList();
        if (CollectionUtils.isEmpty(minusTimeIntervals)) {
            return Lists.newArrayList(originTimeInterval);
        }
        // 先按开始时间排序并合并
        minusTimeIntervals = mergeTimeIntervals(minusTimeIntervals);

        // 和originTimeInterval取交集
        List<TimeInterval> realMinusTimeIntervals = Lists.newArrayList();
        for (TimeInterval minusTimeInterval : minusTimeIntervals) {
            if (minusTimeInterval.getEndTime().isBefore(originTimeInterval.getStartTime())) {
                continue;
            }
            if (minusTimeInterval.getStartTime().isAfter(originTimeInterval.getEndTime())) {
                continue;
            }
            if (minusTimeInterval.getStartTime().isBefore(originTimeInterval.getStartTime())) {
                minusTimeInterval.setStartTime(originTimeInterval.getStartTime());
            }
            if (minusTimeInterval.getEndTime().isAfter(originTimeInterval.getEndTime())) {
                minusTimeInterval.setEndTime(originTimeInterval.getEndTime());
            }
            realMinusTimeIntervals.add(minusTimeInterval);
        }
        // 算差集
        for (TimeInterval minusTimeInterval: realMinusTimeIntervals) {
            if (originTimeInterval.getStartTime().isBefore(minusTimeInterval.getStartTime())) {
                result.add(new TimeInterval(originTimeInterval.getStartTime(), minusTimeInterval.getStartTime()));
            }
            originTimeInterval.setStartTime(minusTimeInterval.getEndTime());
        }
        if (!originTimeInterval.getStartTime().equals(originTimeInterval.getEndTime())) {
            result.add(new TimeInterval(originTimeInterval.getStartTime(), originTimeInterval.getEndTime()));
        }
        return result;
    }

    /**
     * 时间区间输出使用的格式器。
     */
    static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        TimeInterval originTimeInterval = getTimeInterval("2022-02-05 01:01:01", "2022-03-05 01:01:01");
        List<TimeInterval> minusTimeInterval = Lists.newArrayList(
                getTimeInterval("2022-02-05 01:01:02", "2022-02-05 10:00:00"),
                getTimeInterval("2022-02-25 01:01:01", "2022-02-25 10:00:00"),
                getTimeInterval("2022-03-05 00:00:00", "2022-03-05 01:01:00")
        );
    }

    private static TimeInterval getTimeInterval(String beginTime, String endTime) {
        return new TimeInterval(LocalDateTime.parse(beginTime, dateTimeFormatter), LocalDateTime.parse(endTime, dateTimeFormatter));
    }
}
