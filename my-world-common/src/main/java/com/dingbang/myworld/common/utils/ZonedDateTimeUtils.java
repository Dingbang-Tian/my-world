package com.dingbang.myworld.common.utils;

import cn.hutool.core.date.DatePattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * 带时区日期时间处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class ZonedDateTimeUtils {

    /**
     * 二十四点的文本表示。
     */
    public static final String STRING_24_HOUR = "24:00";
    /**
     * 零点的文本表示。
     */
    public static final String STRING_0_HOUR = "00:00";

    /**
     * 获取时区所在日期
     * @param zoneIdStr 时区
     * @return yyyy-MM-dd
     */
    public static String getDateStr(String zoneIdStr) {
        ZoneId zoneId = ZoneId.of(zoneIdStr);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
        return zonedDateTime.format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN));
    }

    /**
     * 获取时区所在日期
     * @param zoneIdStr 时区
     * @return yyyy-MM-dd
     */
    public static String getDateStr(LocalDateTime localDateTime, String zoneIdStr) {
        ZoneId zoneId = ZoneId.of(zoneIdStr);
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(localDateTime.atZone(ZoneId.systemDefault()).toInstant(), zoneId);
        return zonedDateTime.format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN));
    }

    /**
     * 转UTC偏移量
     * @param zoneId
     * @return UTC+hh:mm
     */
    public static String formatUTC(ZoneId zoneId) {
        return "UTC" + ZonedDateTime.now(zoneId).getOffset().getId().replaceAll("Z", "+00:00");
    }

    /**
     * 获取时区所在时间
     * @param zoneIdStr 时区
     * @param datePattern 格式化
     * @return HH:mm
     */
    public static String getDateStr(String zoneIdStr, String datePattern) {
        ZoneId zoneId = ZoneId.of(zoneIdStr);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
        return zonedDateTime.format(DateTimeFormatter.ofPattern(datePattern));
    }


    /**
     * 转成UTC时间生成本地时间
     * @param utcDateTime
     * @return
     */
    public static LocalDateTime convertOffsetTDateTimeLocalDateTime(String utcDateTime) {
        if (StringUtils.isBlank(utcDateTime)) {
            return null;
        }
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        TemporalAccessor parse = dateTimeFormatter.parse(utcDateTime);
        Instant instant = Instant.from(parse);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * 转成UTC时间生成本地时间
     * @param utcDateTime
     * @return
     */
    public static LocalDateTime convertUtcLocalDateTime(String utcDateTime) {
        if (StringUtils.isBlank(utcDateTime)) {
            return null;
        }
        Instant instant = Instant.parse(utcDateTime);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * 把localDateTime 转化为UTC时间字符串
     * @param localDateTime
     * @return
     */
    public static String convertLocalDateTimeToString(LocalDateTime localDateTime) {
        Instant instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        return instant.toString();
    }

    /**
     * 根据本地时区 转化时间戳（秒）为LocalDateTime*
     * @param second second
     * @return
     */
    public static LocalDateTime timestampToLocalDateTime(Long second){
        return LocalDateTime.ofEpochSecond(second, 0, OffsetDateTime.now().getOffset());
    }

    /**
     * 时间戳 （毫秒）转LocalDateTime
     * @param millisecond 毫秒
     * @return
     */
    public static LocalDateTime getDateTimeOfTimestamp(Long millisecond) {
        if(millisecond == null){
            log.error("时间戳转LocalDateTime，timestamp为null");
            return null;
        }
        Instant instant = Instant.ofEpochMilli(millisecond);
        ZoneId zone = ZoneId.systemDefault();
        return LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * LocalDateTime转时间戳
     * @param localDateTime
     * @return
     */
    public static Long getTimestampOfDateTime(LocalDateTime localDateTime) {
        if(localDateTime == null){
            log.error("LocalDateTime转时间戳，localDateTime为null");
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDateTime.atZone(zone).toInstant();
        return instant.toEpochMilli();
    }

    /**
     * LocalDateTime转为自定义的时间格式的字符串
     * @param localDateTime
     * @param format
     * @return
     */
    public static String getDateTimeAsString(LocalDateTime localDateTime, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return localDateTime.format(formatter);
    }

    /**
     * 时间字符串转为自定义时间格式的LocalDateTime
     * @param time
     * @param format
     * @return
     */
    public static LocalDateTime parseStringToDateTime(String time, String format) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern(format);
        return LocalDateTime.parse(time, df);
    }

    /**
     * Date类型转LocalDateTime
     * @see com.dingbang.myworld.common.utils.time.GlobalDateConvertUtils#getLocalDateTime(Date)
     * @param date
     * @return
     */
    public static LocalDateTime dateToLocalDate(Date date){
        if(date == null){
            log.error("Date类型转LocalDateTime，date为null");
            return null;
        }
        //获取时间实例
        Instant instant = date.toInstant();
        //获取时间地区ID
        ZoneId zoneId = ZoneId.systemDefault();
        //转换为LocalDate
        LocalDateTime localDateTime = instant.atZone(zoneId).toLocalDateTime();
        return localDateTime;
    }

    /**
     * 将时间从系统默认时区转成给定的时区
     * @see com.dingbang.myworld.common.utils.time.GlobalDateConvertUtils#getLocalDateTime(Date, ZoneOffset)
     * @param localDateTime 时间
     * @param toZoneId 目标时区
     * @return 转换后时间
     */
    public static LocalDateTime convertTimeZoneFromSystemZone(LocalDateTime localDateTime, String toZoneId) {
        if (StringUtils.isBlank(toZoneId) || localDateTime == null) {
            return localDateTime;
        }
        return LocalDateTime.ofInstant(localDateTime.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.of(toZoneId));
    }

    /**
     * 将时间从给定时区转成系统默认时区
     * @see com.dingbang.myworld.common.utils.time.GlobalDateConvertUtils#getLocalDateTime(ZoneOffset, LocalDateTime)
     * @param localDateTime 时间
     * @param fromZoneId 目标时区
     * @return 转换后时间
     */
    public static LocalDateTime convertTimeZoneToSystemZone(LocalDateTime localDateTime, String fromZoneId) {
        if (StringUtils.isBlank(fromZoneId) || localDateTime == null) {
            return localDateTime;
        }
        return LocalDateTime.ofInstant(localDateTime.atZone(ZoneId.of(fromZoneId)).toInstant(), ZoneId.systemDefault());
    }

    /**
     * 将时间从系统默认时区转成给定的时区
     * @see com.dingbang.myworld.common.utils.time.GlobalDateConvertUtils#getLocalDateTime(LocalDateTime, ZoneId, ZoneId)
     * @param localDateTime 时间
     * @param source 源时区
     * @param target 目标时区
     * @return 转换后时间
     */
    public static LocalDateTime convertTimeZone(LocalDateTime localDateTime, ZoneId source, ZoneId target) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.ofInstant(localDateTime.atZone(source).toInstant(), target);
    }

    /**
     * 获取当天0点时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#startOfTheDay(LocalDateTime)
     * @param localDateTime 时间
     * @return 当天0点
     */
    @Deprecated
    public static LocalDateTime startOfTheDay(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MIN);
    }

    /**
     * 当周起始时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#startOfTheWeek(LocalDateTime)
     * @param localDateTime 时间
     * @return 当周起始时间
     */
    @Deprecated
    public static LocalDateTime startOfTheWeek(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.with(DayOfWeek.MONDAY).toLocalDate(), LocalTime.MIN);
    }

    /**
     * 当月起始时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#startOfTheMonth(LocalDateTime)
     * @param localDateTime 时间
     * @return 当月起始时间
     */
    @Deprecated
    public static LocalDateTime startOfTheMonth(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate(), LocalTime.MIN);
    }

    /**
     * 获取当天最晚时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#endOfTheDay(LocalDateTime)
     * @param localDateTime 时间
     * @return 当天最晚时间
     */
    @Deprecated
    public static LocalDateTime endOfTheDay(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MAX);
    }

    /**
     * 当周最晚时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#endOfTheWeek(LocalDateTime)
     * @param localDateTime 时间
     * @return 当周最晚时间
     */
    @Deprecated
    public static LocalDateTime endOfTheWeek(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.with(DayOfWeek.SUNDAY).toLocalDate(), LocalTime.MAX);
    }

    /**
     * 当月最晚时间
     * @see com.dingbang.myworld.common.utils.time.GlobalDateCalculateUtils#endOfTheMonth(LocalDateTime)
     * @param localDateTime 时间
     * @return 当月最晚时间
     */
    @Deprecated
    public static LocalDateTime endOfTheMonth(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return LocalDateTime.of(localDateTime.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate(), LocalTime.MAX);
    }

    /**
     * 组合LocalDate和23:33文本类型的时间为LocalDateTime
     * @param localDate 日期
     * @param time 23:33 HH:mm格式的时间
     * @return LocalDateTime
     */
    public static LocalDateTime getLocalDateTime(LocalDate localDate, String time) {
        if (STRING_24_HOUR.equals(time)) {
            LocalTime localTime = LocalTime.parse(STRING_0_HOUR);
            LocalDate nextDay = localDate.plusDays(1);
            return LocalDateTime.of(nextDay, localTime);
        } else {
            LocalTime localTime = LocalTime.parse(time);
            return LocalDateTime.of(localDate, localTime);
        }
    }
}
