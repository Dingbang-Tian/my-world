package com.dingbang.myworld.common.utils.time;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.TimeZone;

/**
 * 国际化日期时间处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Deprecated
public class GlobalDateUtils {

    /**
     * 转时间戳
     * @see GlobalDateConvertUtils#getTime(Date)
     * @param date
     * @return
     */
    public static Long toLong(Date date) {
        if (date == null) {
            return null;
        }
        return date.getTime();
    }

    /**
     * 转时间戳
     * @see GlobalDateConvertUtils#getDate(Long)
     * @param date
     * @return
     */
    public static Date toDate(Long date) {
        if (date == null) {
            return null;
        }
        return new Date(date);
    }

    /**
     * 获取时区对象
     * @see GlobalTimeZoneUtils#getTimeZone(String)
     * @param timeZone
     * @return
     */
    public static TimeZone getTimeZone(String timeZone) {
        if (StringUtils.isEmpty(timeZone)) {
            return null;
        }
        int utc = StringUtils.indexOf(timeZone, "UTC");
        boolean replace = utc == 0;
        if (replace) {
            timeZone = "GMT" + timeZone.substring(3);
        }
        return TimeZone.getTimeZone(timeZone);
    }


    /**
     * 转时间
     * @see GlobalDateConvertUtils#getLocalDateTime(Date, ZoneOffset)
     * @param date
     * @return
     */
    @Deprecated
    public static LocalDateTime toLocalDateTime(Date date) {
        return toLocalDateTime(date, TimeZone.getDefault());
    }


    /**
     * 转时间
     * @see GlobalDateConvertUtils#getLocalDateTime(Date, ZoneOffset)
     * @param date
     * @return
     */
    public static LocalDateTime toLocalDateTime(Date date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return date.toInstant().atZone(timeZone.toZoneId()).toLocalDateTime();
    }


    /**
     * 转时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @return
     */
    @Deprecated
    public static Date toDate(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return toDate(date, TimeZone.getDefault());
    }

    /**
     * 转时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @return
     */
    @Deprecated
    public static Date toDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return toDate(date, TimeZone.getDefault());
    }

    /**
     * 转成时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @param timeZone
     * @return
     */
    public static Date toDate(LocalDateTime date, String timeZone) {
        return toDate(date, getTimeZone(timeZone));
    }

    /**
     * 转成时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @param timeZone
     * @return
     */
    public static Date toDate(LocalDate date, String timeZone) {
        return toDate(date, getTimeZone(timeZone));
    }

    /**
     * 转成时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @param timeZone
     * @return
     */
    public static Date toDate(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return Date.from(date.atZone(timeZone.toZoneId()).toInstant());
    }

    /**
     * 转成时间戳
     * @see GlobalDateConvertUtils#getDate(LocalDateTime, ZoneOffset)
     * @param date
     * @param timeZone
     * @return
     */
    public static Date toDate(LocalDate date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return Date.from(date.atStartOfDay(timeZone.toZoneId()).toInstant());
    }

    /**
     * @see GlobalDateCalculateUtils#startOfTheDay(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atStartOfDay(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return Date.from(date.with(LocalTime.MIN).atZone(timeZone.toZoneId()).toInstant());
    }

    /**
     * @see GlobalDateCalculateUtils#endOfTheDay(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atEndOfDay(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return Date.from(date.with(LocalTime.MAX).atZone(timeZone.toZoneId()).toInstant());
    }

    /**
     * @see GlobalDateCalculateUtils#endOfTheHour(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atStartOfHour(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        LocalDateTime localDateTime = date.truncatedTo(ChronoUnit.HOURS);
        return toDate(localDateTime, timeZone);
    }

    /**
     * @see GlobalDateCalculateUtils#endOfTheHour(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atEndOfHour(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        LocalDateTime localDateTime = date.truncatedTo(ChronoUnit.HOURS).plusHours(1).minusNanos(1);
        return toDate(localDateTime, timeZone);
    }

    /**
     * @see GlobalDateCalculateUtils#startOfTheMonth(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atStartOfMonth(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        LocalDate localDateTime = LocalDate.of(date.getYear(), date.getMonth(), 1);
        return toDate(localDateTime, timeZone);
    }

    /**
     * @see GlobalDateCalculateUtils#endOfTheMonth(LocalDateTime)
     * @param date
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date atEndOfMonth(LocalDateTime date, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        LocalDate localDate = LocalDate.of(date.getYear(), date.getMonth(), 1);
        LocalDateTime localDateTime = localDate.atStartOfDay().plusMonths(1).minusNanos(1);
        return toDate(localDateTime, timeZone);
    }

    /**
     * 基于时区截取
     *
     * @param date
     * @param unit
     * @param timeZone
     * @return
     */
    @Deprecated
    public static Date truncatedTo(LocalDateTime date, TemporalUnit unit, TimeZone timeZone) {
        if (date == null || timeZone == null) {
            return null;
        }
        return Date.from(date.truncatedTo(unit).atZone(timeZone.toZoneId()).toInstant());
    }


    /**
     * 格式化
     *
     * @param date
     * @param timeZone GMT+9:00 GMT+9 UTC+9 UTC+9:00
     * @param format
     * @return
     */
    @Deprecated
    public static String format(Date date, String timeZone, String format) {
        TimeZone zone = getTimeZone(timeZone);
        String format1 = DateFormatUtils.format(date, format, zone);
        return format1;
    }

}
