package com.dingbang.myworld.common.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Date;

/**
 * 日期、时间类型与时间戳转换工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Dates {

    /**
     * 由java.time下的日期对象，转换为java.util.Date对象
     *
     * @param t
     * @param <T>
     * @return
     */
    public static <T extends Temporal> Date date(T t) {
        return DateUtil.date(t);
    }

    /**
     * 由java.util.Date对象，转换为LocalDateTime
     *
     * @param date
     * @return
     */
    public static LocalDateTime local(Date date) {
        return LocalDateTimeUtil.of(date);
    }

    /**
     * 毫秒时间戳转换为LocalDateTime
     *
     * @param epochMilli
     * @return
     */
    public static LocalDateTime local(Long epochMilli) {
        return zoned(epochMilli).toLocalDateTime();
    }

    /**
     * 从java.util.Date转换为ZonedDateTime
     *
     * @param date
     * @return
     */
    public static ZonedDateTime zoned(Date date) {
        return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 毫秒时间戳转换为ZonedDateTime
     *
     * @param epochMilli
     * @return
     */
    public static ZonedDateTime zoned(Long epochMilli) {
        return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault());
    }

    /**
     * 将java.time包下的对象根据指定格式转换为 String
     *
     * @param t       日期
     * @param pattern 格式
     * @param <T>     T
     * @return
     */
    public static <T extends Temporal> String toStr(T t, String pattern) {
        if (t == null) {
            return null;
        }
        return DatePattern.createFormatter(pattern).format(t);
    }

    /**
     * 将java.time下的对象 转换为 String
     *
     * @param t   日期
     * @param <T> T
     * @return
     */
    public static <T extends Temporal> String toStr(T t) {
        if (t == null) {
            return null;
        }
        String result;
        if (t instanceof LocalDateTime) {
            result = DatePattern.NORM_DATETIME_FORMATTER.format(t);
        } else if (t instanceof LocalTime) {
            result = DatePattern.NORM_TIME_FORMATTER.format(t);
        } else if (t instanceof OffsetTime) {
            result = DatePattern.NORM_TIME_FORMATTER.format(t);
        } else {
            result = t.toString();
        }
        return result;
    }

    /**
     * 下一秒对应的时间对象，如果所传入对象不支持秒，则会返回下一个最小时间单位的对象
     *
     * @param t
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Temporal> T next(T t) {
        if (t instanceof LocalDate) {
            return (T) ((LocalDate) t).plusDays(1);
        } else if (t instanceof YearMonth) {
            return (T) ((YearMonth) t).plusMonths(1);
        } else if (t instanceof Year) {
            return (T) ((Year) t).plusYears(1);
        } else if (t instanceof LocalTime) {
            return (T) ((LocalTime) t).plusSeconds(1);
        } else if (t instanceof LocalDateTime) {
            return (T) ((LocalDateTime) t).plusSeconds(1);
        } else if (t instanceof OffsetDateTime) {
            return (T) ((OffsetDateTime) t).plusSeconds(1);
        } else if (t instanceof ZonedDateTime) {
            return (T) ((ZonedDateTime) t).plusSeconds(1);
        } else if (t instanceof OffsetTime) {
            return (T) ((OffsetTime) t).plusSeconds(1);
        } else if (t instanceof Instant) {
            return (T) ((Instant) t).plusSeconds(1);
        }
        return null;
    }

    /**
     * 上一秒对应的时间对象，如果所传入对象不支持秒，则会返回上一个最小时间单位的对象
     *
     * @param t
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Temporal> T previous(T t) {
        if (t instanceof LocalDate) {
            return (T) ((LocalDate) t).minusDays(1);
        } else if (t instanceof YearMonth) {
            return (T) ((YearMonth) t).minusMonths(1);
        } else if (t instanceof Year) {
            return (T) ((Year) t).minusYears(1);
        } else if (t instanceof LocalTime) {
            return (T) ((LocalTime) t).minusSeconds(1);
        } else if (t instanceof LocalDateTime) {
            return (T) ((LocalDateTime) t).minusSeconds(1);
        } else if (t instanceof OffsetDateTime) {
            return (T) ((OffsetDateTime) t).minusSeconds(1);
        } else if (t instanceof ZonedDateTime) {
            return (T) ((ZonedDateTime) t).minusSeconds(1);
        } else if (t instanceof OffsetTime) {
            return (T) ((OffsetTime) t).minusSeconds(1);
        } else if (t instanceof Instant) {
            return (T) ((Instant) t).minusSeconds(1);
        }
        return null;
    }

    /**
     * 将系统时区的LocalDateTime转换为毫秒时间戳
     *
     * @param localDateTime
     * @return
     */
    public static Long toEpochMilli(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 将系统时区的LocalDateTime转换为指定时区的ZonedDateTime
     *
     * @param localDateTime
     * @param targetZoneId
     * @return
     */
    public static ZonedDateTime toTargetZoned(LocalDateTime localDateTime, String targetZoneId) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        return sourceZonedDateTime.withZoneSameInstant(ZoneId.of(targetZoneId));
    }

    /**
     * 将系统时区的LocalDateTime转换为指定时区的ZonedDateTime
     *
     * @param localDateTime
     * @param targetZoneId
     * @return
     */
    public static ZonedDateTime toTargetZoned(LocalDateTime localDateTime, ZoneId targetZoneId) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        return sourceZonedDateTime.withZoneSameInstant(targetZoneId);
    }

    /**
     * 将指定时区的LocalDateTime转换为系统时区的ZonedDateTime
     *
     * @param localDateTime
     * @param sourceZoneId
     * @return
     */
    public static ZonedDateTime toSystemZoned(LocalDateTime localDateTime, String sourceZoneId) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(ZoneId.of(sourceZoneId));
        return sourceZonedDateTime.withZoneSameInstant(ZoneId.systemDefault());
    }

    /**
     * 将指定时区的LocalDateTime转换为系统时区的ZonedDateTime
     *
     * @param localDateTime
     * @param sourceZoneId
     * @return
     */
    public static ZonedDateTime toSystemZoned(LocalDateTime localDateTime, ZoneId sourceZoneId) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(sourceZoneId);
        return sourceZonedDateTime.withZoneSameInstant(ZoneId.systemDefault());
    }

    /**
     * 将指定时区的LocalDateTime转换为指定时区的ZonedDateTime
     *
     * @param localDateTime
     * @param sourceZone
     * @param targetZone
     * @return
     */
    public static ZonedDateTime toTargetZoned(LocalDateTime localDateTime, String sourceZone, String targetZone) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(ZoneId.of(sourceZone));
        return sourceZonedDateTime.withZoneSameInstant(ZoneId.of(targetZone));
    }

    /**
     * 将指定时区的LocalDateTime转换为指定时区的ZonedDateTime
     *
     * @param localDateTime
     * @param sourceZone
     * @param targetZone
     * @return
     */
    public static ZonedDateTime toTargetZoned(LocalDateTime localDateTime, ZoneId sourceZone, ZoneId targetZone) {
        ZonedDateTime sourceZonedDateTime = localDateTime.atZone(sourceZone);
        return sourceZonedDateTime.withZoneSameInstant(targetZone);
    }

}
