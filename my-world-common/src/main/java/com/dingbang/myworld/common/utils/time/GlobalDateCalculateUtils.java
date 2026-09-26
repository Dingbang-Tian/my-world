package com.dingbang.myworld.common.utils.time;

import org.apache.commons.lang3.tuple.Pair;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Objects;

/**
 * 国际化日期时间计算工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class GlobalDateCalculateUtils {


    /**
     * 截秒，秒的开始
     *
     * @param date 2024-12-31T23:59:59.999999999
     * @return 2024-12-31T23:59:59
     */
    public static LocalDateTime truncatedToSecond(LocalDateTime date) {
        return date == null ? null : date.truncatedTo(ChronoUnit.SECONDS);
    }


    /**
     * 小时的开始
     *
     * @param date
     * @return
     */
    public static LocalDateTime startOfTheHour(LocalDateTime date) {
        return date == null ? null : date.truncatedTo(ChronoUnit.HOURS);
    }

    /**
     * 小时的结束
     *
     * @param date
     * @return
     */
    public static LocalDateTime endOfTheHour(LocalDateTime date) {
        return date == null ? null : date.truncatedTo(ChronoUnit.HOURS).plusHours(1).minusNanos(1);
    }

    /**
     * 获取当天0点时间
     *
     * @param localDateTime 时间
     * @return 当天0点
     */
    public static LocalDateTime startOfTheDay(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(LocalTime.MIN);
    }

    /**
     * 获取当天最晚时间
     *
     * @param localDateTime 时间
     * @return 当天最晚时间
     */
    public static LocalDateTime endOfTheDay(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(LocalTime.MAX);
    }

    /**
     * 当周起始时间。以周一作为一周开始
     *
     * @param localDateTime 时间
     * @return 当周起始时间
     */
    public static LocalDateTime startOfTheWeek(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(DayOfWeek.MONDAY).with(LocalTime.MIN);
    }

    /**
     * 当周最晚时间。以周一作为一周开始
     *
     * @param localDateTime 时间
     * @return 当周最晚时间
     */
    public static LocalDateTime endOfTheWeek(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(DayOfWeek.SUNDAY).with(LocalTime.MAX);
    }

    /**
     * 当月起始时间
     *
     * @param localDateTime 时间
     * @return 当月起始时间
     */
    public static LocalDateTime startOfTheMonth(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN);
    }


    /**
     * 当月最晚时间
     *
     * @param localDateTime 时间
     * @return 当月最晚时间
     */
    public static LocalDateTime endOfTheMonth(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
    }


    /**
     * 年的开始
     *
     * @param date
     * @return
     */
    public static LocalDateTime startOfTheYear(LocalDateTime date) {
        return date == null ? null : LocalDateTime.of(date.getYear(), 1, 1, 0, 0, 0);
    }

    /**
     * 年的结束
     *
     * @param date
     * @return
     */
    public static LocalDateTime endOfTheYear(LocalDateTime date) {
        return date == null ? null : startOfTheYear(date).plusYears(1).minusNanos(1);
    }

    /**
     * 获取指定本地时间在指定时区下当天的开始、截止时间
     *
     * @param tenantLocalDate 本地时间
     * @param zoneIdStr       时区
     * @return 当天起止 Date
     */
    public static Pair<Date, Date> getZoneDayDateRange(LocalDateTime tenantLocalDate, String zoneIdStr) {
        LocalDateTime startOfDay = startOfTheDay(tenantLocalDate);
        LocalDateTime endOfDay = endOfTheDay(startOfDay);
        ZoneId zoneId = GlobalTimeZoneUtils.getZoneId(zoneIdStr);
        Date st = GlobalDateConvertUtils.getDate(startOfDay, zoneId);
        Date ed = GlobalDateConvertUtils.getDate(endOfDay, zoneId);
        return Pair.of(st, ed);
    }

    /**
     * 获取指定时间在指定时区下当天的开始、截止时间
     *
     * @param date      时间
     * @param zoneIdStr 时区
     * @return 当天起止 Date
     */
    public static Pair<Date, Date> getZoneDayDateRange(Date date, String zoneIdStr) {
        ZoneId zoneId = GlobalTimeZoneUtils.getZoneId(zoneIdStr);
        LocalDateTime localDateTime = GlobalDateConvertUtils.getLocalDateTime(date, zoneId);
        return getZoneDayDateRange(localDateTime, zoneIdStr);
    }

    /**
     * 判断指定时间在给定时区下是否为今天
     *
     * @param time        时间
     * @param timeZoneStr 时区
     * @return 是否今天
     */
    public static boolean isToday(Date time, String timeZoneStr) {
        ZoneId zoneId = GlobalTimeZoneUtils.getZoneId(timeZoneStr);
        LocalDateTime localDateTime = GlobalDateConvertUtils.getLocalDateTime(LocalDateTime.now(), zoneId);
        LocalDateTime orderLocalTime = GlobalDateConvertUtils.getLocalDateTime(time, zoneId);
        return Objects.equals(localDateTime.toLocalDate(), orderLocalTime.toLocalDate());
    }

    /**
     * 判断两个时间在指定时区下是否为同一天
     *
     * @param date1       时间1
     * @param date2       时间2
     * @param timeZoneStr 时区
     * @return 是否同一天
     */
    public static boolean isSameDay(Date date1, Date date2, String timeZoneStr) {
        if (date1 == null || date2 == null) {
            return false;
        }
        ZoneId zoneId = GlobalTimeZoneUtils.getZoneId(timeZoneStr);
        LocalDateTime local1 = GlobalDateConvertUtils.getLocalDateTime(date1, zoneId);
        LocalDateTime local2 = GlobalDateConvertUtils.getLocalDateTime(date2, zoneId);
        return Objects.equals(local1.toLocalDate(), local2.toLocalDate());
    }
}
