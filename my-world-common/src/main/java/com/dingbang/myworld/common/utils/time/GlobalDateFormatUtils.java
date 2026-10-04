package com.dingbang.myworld.common.utils.time;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

import static com.dingbang.myworld.common.utils.time.GlobalTimeZoneUtils.getTimeZone;

/**
 * 国际化日期时间格式化工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class GlobalDateFormatUtils {

    /**
     * ISO_LOCAL_DATE:yyyy-MM-dd
     */
    public final static String LOCAL_DATE = "yyyy-MM-dd";

    /**
     * 年月:yyyy-MM
     */
    public final static String LOCAL_DATE_MONTH = "yyyy-MM";

    /**
     * ISO_LOCAL_TIME:HH:mm:ss
     */
    public final static String LOCAL_TIME = "HH:mm:ss";

    /**
     * LOCAL_DATE_TIME
     */
    public final static String LOCAL_DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    /**
     * ISO 日期时间格式文本。
     */
    public final static String ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    /**
     * 格式化Date
     *
     * @param date
     * @param timeZone GMT+9:00 GMT+9 UTC+9 UTC+9:00等
     * @param format
     * @return
     */
    public static String format(Date date, String format, String timeZone) {
        TimeZone zone = getTimeZone(timeZone);
        return format(date, format, zone);
    }

    /**
     * 格式化对应时区时间
     *
     * @param date
     * @param format
     * @param zone
     * @return
     */
    public static String format(Date date, String format, TimeZone zone) {
        String format1 = DateFormatUtils.format(date, format, zone);
        return format1;
    }


    /**
     * 格式化Date,使用系统时区
     *
     * @param date
     * @param format
     * @return
     */
    public static String format(Date date, String format) {
        String format1 = DateFormatUtils.format(date, format);
        return format1;
    }


    /**
     * 格式化本地时间
     *
     * @param date
     * @param format
     * @return
     */
    public static String format(LocalDateTime date, String format) {
        return DateTimeFormatter.ofPattern(format).format(date);
    }

    /**
     * 解析日期字符串，字面量解析
     *
     * @param date
     * @param format
     * @return
     */
    public static LocalDateTime parseLocal(String date, String format) {
        try {
            return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(format));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析日期
     *
     * @param date
     * @param format
     * @return
     */
    public static LocalDate parseLocalDate(String date, String format) {
        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern(format));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 解析日期字符串，使用当前时区
     *
     * @param date
     * @param format
     * @return
     */
    public static Date parse(String date, String format) {
        try {
            return DateUtils.parseDate(date, format);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析时间.注意使用当前的事情便宜。夏令时可能存在问题
     *
     * @param date     相关时区的展示字符串
     * @param format   字符串格式
     * @param timeZone 相关时区
     * @return
     */
    public static Date parse(String date, String format, String timeZone) {
        TimeZone zoneOffset = GlobalTimeZoneUtils.getTimeZone(timeZone);
        return parse(date, format, zoneOffset);
    }


    /**
     * 解析时间
     *
     * @param date
     * @param format
     * @param timeZone
     * @return
     */
    public static Date parse(String date, String format, TimeZone timeZone) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
            simpleDateFormat.setTimeZone(timeZone);
            return date == null ? null : simpleDateFormat.parse(date);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
