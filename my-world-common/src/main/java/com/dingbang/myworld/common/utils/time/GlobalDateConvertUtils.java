package com.dingbang.myworld.common.utils.time;

import java.time.*;
import java.util.Date;

/**
 * 国际化日期时间类型转换工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class GlobalDateConvertUtils {

    /**
     * 转换成时间戳
     *
     * @param date
     * @return 返回UTC0的毫秒数
     */
    public static Long getTime(Date date) {
        return date == null ? null : date.getTime();
    }

    /**
     * 获取时间戳
     *
     * @param date
     * @return
     */
    public static Long getTime(ZonedDateTime date) {
        return date == null ? null : date.toInstant().toEpochMilli();
    }

    /**
     * 获取时间戳
     *
     * @param date
     * @return
     */
    public static Long getTime(OffsetDateTime date) {
        return date == null ? null : date.toInstant().toEpochMilli();
    }

    /**
     * 转换成时间戳，本地时间+时区偏移。谨慎使用
     *
     * @param dateTime
     * @param zoneOffset
     * @return 返回UTC0的毫秒数
     */
    public static Long getTime(LocalDateTime dateTime, ZoneOffset zoneOffset) {
        return dateTime == null ? null : dateTime.toInstant(zoneOffset).toEpochMilli();
    }


    /**
     * 获取指定时区的绝对时间错
     *
     * @param dateTime
     * @param zoneId
     * @return
     */
    public static Long getTime(LocalDateTime dateTime, ZoneId zoneId) {
        ZonedDateTime zonedDate = getZonedDate(dateTime, zoneId);
        return zonedDate == null ? null : getTime(zonedDate);
    }


    /**
     * 转换成时刻
     *
     * @param dateTime
     * @param zoneId
     * @return
     */
    public static Instant getInstant(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime == null ? null : dateTime.atZone(zoneId).toInstant();
    }


    /**
     * 获取时刻
     *
     * @param date
     * @return
     */
    public static Instant getInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /**
     * 获取时刻
     *
     * @param time
     * @return
     */
    public static Instant getInstant(Long time) {
        return time == null ? null : new Date(time).toInstant();
    }


    /**
     * 时间戳转换成Date
     *
     * @param time
     * @return
     */
    public static Date getDate(Long time) {
        return time == null ? null : new Date(time);
    }

    /**
     * 本地时间转换成Date，本地时间+时区偏移
     *
     * @param dateTime
     * @param zoneOffset
     * @return
     */
    public static Date getDate(LocalDateTime dateTime, ZoneOffset zoneOffset) {
        return getDate(getTime(dateTime, zoneOffset));
    }

    /**
     * 本地时间转换成Date，本地时间+时区；注意夏令时开启和结束时的偏移顺序
     *
     *
     * @param dateTime
     * @param zoneId
     * @return
     */
    public static Date getDate(LocalDateTime dateTime, ZoneId zoneId) {
        return getDate(getTime(dateTime, zoneId));
    }

    /**
     * 转换成系统时间
     *
     * @param dateTime
     * @return
     */
    public static Date getDate(LocalDateTime dateTime) {
        return getDate(getTime(dateTime, ZoneId.systemDefault()));
    }

    /**
     * 时间转换
     *
     * @param dateTime
     * @return
     */
    public static Date getDate(ZonedDateTime dateTime) {
        return getDate(getTime(dateTime));
    }


    /**
     * 时间转换ZonedDateTime
     *
     * @param dateTime
     * @param zoneId
     * @return
     */
    public static ZonedDateTime getZonedDate(Date dateTime, ZoneId zoneId) {
        return getZonedDate(getTime(dateTime), zoneId);
    }


    /**
     * 时间戳转换成ZonedDateTime
     *
     * @param time
     * @param zoneId
     * @return
     */
    public static ZonedDateTime getZonedDate(Long time, ZoneId zoneId) {
        return time == null ? null : getZonedDate(getLocalDateTime(time, zoneId), zoneId);
    }

    /**
     * 本地时间转换成ZonedDateTime，本地时间+时区偏移
     *
     * @param dateTime
     * @param zoneOffset
     * @return
     */
    public static ZonedDateTime getZonedDate(LocalDateTime dateTime, ZoneOffset zoneOffset) {
        return ZonedDateTime.of(dateTime, zoneOffset);
    }

    /**
     * 本地时间转换成ZonedDateTime，本地时间+时区.适配夏令时
     *
     * @param dateTime
     * @param zoneId
     * @return
     */
    public static ZonedDateTime getZonedDate(LocalDateTime dateTime, ZoneId zoneId) {
        return ZonedDateTime.of(dateTime, zoneId);
    }


    /**
     * 转换成本地时间
     *
     * @param instant
     * @param zoneOffset
     * @return
     */
    public static LocalDateTime getLocalDateTime(Instant instant, ZoneOffset zoneOffset) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zoneOffset);
    }

    /**
     * 转换成本地时间
     *
     * @param instant
     * @param zoneId
     * @return
     */
    public static LocalDateTime getLocalDateTime(Instant instant, ZoneId zoneId) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zoneId);
    }

    /**
     * 转换成本地时间
     *
     * @param time
     * @param zoneId 虽然支持夏令时，但结果可能是未定的，所以不建议使用
     * @return
     */
    public static LocalDateTime getLocalDateTime(Long time, ZoneId zoneId) {
        return time == null ? null : LocalDateTime.ofInstant(getInstant(time), zoneId);
    }


    /**
     * 转换成本地时间
     *
     * @param date
     * @param zoneOffset
     * @return
     */
    public static LocalDateTime getLocalDateTime(Date date, ZoneOffset zoneOffset) {
        return getLocalDateTime(getInstant(date), zoneOffset);
    }

    /**
     * 转换成本地时间
     *
     * @param date
     * @param zoneId
     * @return
     */
    public static LocalDateTime getLocalDateTime(Date date, ZoneId zoneId) {
        return getLocalDateTime(getInstant(date), zoneId);
    }

    /**
     * 转换成本地时间,使用系统时区
     *
     * @param date
     * @return
     */
    public static LocalDateTime getLocalDateTime(Date date) {
        Instant instant = getInstant(date);
        ZoneOffset zoneOffsetDefault = GlobalTimeZoneUtils.getZoneOffsetDefault(instant);
        return getLocalDateTime(instant, zoneOffsetDefault);
    }

    /**
     * 返回持有时区的本地时间
     *
     * @param date
     * @return
     */
    public static LocalDateTime getLocalDateTime(ZonedDateTime date) {
        return date == null ? null : date.toLocalDateTime();
    }

    /**
     * 返回持有时区的本地时间
     *
     * @param date
     * @return
     */
    public static LocalDateTime getLocalDateTime(OffsetDateTime date) {
        return date == null ? null : date.toLocalDateTime();
    }

    /**
     * 本地时间转换成本地时间，不建议直接转换。先本地时间转换成绝对时间，再转换为目标地本地时间
     *
     * @param localDateTime
     * @param srcZoneOffset  原时区,使用绝对偏移量
     * @param destZoneOffset 默认时区,使用绝对偏移量
     * @return
     */
    public static LocalDateTime getLocalDateTime(LocalDateTime localDateTime, ZoneOffset srcZoneOffset, ZoneOffset destZoneOffset) {
        return getLocalDateTime(getTime(localDateTime, srcZoneOffset), destZoneOffset);
    }

    /**
     * 本地时间转换成本地时间，不建议直接转换。先本地时间转换成绝对时间，再转换为目标地本地时间
     *
     * @param localDateTime
     * @param srcZoneId     原时区,使用绝对偏移量
     * @param destZoneId    默认时区,使用绝对偏移量
     * @return
     */
    public static LocalDateTime getLocalDateTime(LocalDateTime localDateTime, ZoneId srcZoneId, ZoneId destZoneId) {
        return LocalDateTime.ofInstant(localDateTime.atZone(srcZoneId).toInstant(), destZoneId);
    }

    /**
     * 系统时区的本地时间，转换成目标时区的本地时间
     *
     * @param localDateTime
     * @param destZoneId
     * @return
     */
    public static LocalDateTime getLocalDateTime(LocalDateTime localDateTime, ZoneId destZoneId) {
        return getLocalDateTime(localDateTime, GlobalTimeZoneUtils.getZoneIdDefault(), destZoneId);
    }

    /**
     * 指定时区本地时间，转换成系统时区的本地时间
     *
     * @param localDateTime
     * @param srcZoneId
     * @return
     */
    public static LocalDateTime getLocalDateTime(ZoneId srcZoneId, LocalDateTime localDateTime) {
        return getLocalDateTime(localDateTime, srcZoneId, GlobalTimeZoneUtils.getZoneIdDefault());
    }
}
