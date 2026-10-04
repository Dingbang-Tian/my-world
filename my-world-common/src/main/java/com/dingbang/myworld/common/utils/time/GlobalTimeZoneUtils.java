package com.dingbang.myworld.common.utils.time;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

/**
 * 国际化时区处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class GlobalTimeZoneUtils {

    /**
     * UTC 时区标识。
     */
    private final static String UTC = "UTC";
    /**
     * UT 时区标识。
     */
    private final static String UT = "UT";
    /**
     * GMT 时区标识。
     */
    private final static String GMT = "GMT";

    /**
     * 正时区偏移符号。
     */
    private final static String PLUS = "+";

    /**
     * 负时区偏移符号。
     */
    private final static String MINUS = "-";

    /**
     * 0时区
     */
    public final static String UTC_0 = "UTC+00:00";

    /**
     * 东八区
     */
    public final static String UTC_8 = "UTC+08:00";


    /**
     * 获取时区对象
     *
     * @param timeZone UTC+09:00,UTC+9,UT+08:00,GMT+09:00,GMT+9,GMT+9:00,+8,-9,+8:00,CST,Z 等 <br/>
     *                 不推荐区域表示法，不精准：Asia/Shanghai,America/New_York
     * @return
     */
    public static TimeZone getTimeZone(String timeZone) {
        //TODO 性能优化
        if (StringUtils.isEmpty(timeZone)) {
            return null;
        }
        if (StringUtils.startsWith(timeZone, UTC)) {
            timeZone = GMT + timeZone.substring(3);
        } else if (StringUtils.startsWith(timeZone, UT)) {
            timeZone = GMT + timeZone.substring(2);
        } else if (StringUtils.startsWith(timeZone, PLUS) || StringUtils.startsWith(timeZone, MINUS)) {
            timeZone = GMT + timeZone;
        }
        if (timeZone.startsWith(GMT)) {
            return TimeZone.getTimeZone(timeZone);
        }
        // 一些其他写法：CST、Z
        ZoneId zoneId;
        if (!ZoneId.SHORT_IDS.containsKey(timeZone)) {
            zoneId = ZoneId.of(timeZone);
        } else {
            zoneId = ZoneId.of(ZoneId.SHORT_IDS.get(timeZone));
        }
        return TimeZone.getTimeZone(zoneId);
    }


    /**
     * 当前指定时间/当前时间的时区缩写，例如 CST
     *
     * @param zoneId
     * @return
     */
    public static String getShortName(ZoneId zoneId, Date date) {
        if (date == null) {
            date = new Date();
        }
        String abbreviation = ZonedDateTime.ofInstant(date.toInstant(), zoneId).format(DateTimeFormatter.ofPattern("z"));
        return abbreviation;
    }

    /**
     * 获取系统默认时区
     *
     * @return
     */
    public static TimeZone getTimeZoneDefault() {
        return TimeZone.getDefault();
    }

    /**
     * 获取时区ID
     *
     * @param timeZone
     * @return
     */
    public static ZoneId getZoneId(TimeZone timeZone) {
        return timeZone == null ? null : timeZone.toZoneId();
    }

    /**
     * 获取时区ID
     *
     * @param zoneId
     * @return
     */
    public static ZoneId getZoneId(String zoneId) {
        return zoneId == null ? null : ZoneId.of(zoneId);
    }

    /**
     * 获取时区偏移量,基于最新时间
     *
     * @param zoneId
     * @return
     */
    public static ZoneOffset getZoneOffset(ZoneId zoneId) {
        if (zoneId == null) {
            return null;
        }
        return getZoneOffset(zoneId, Instant.now());
    }

    /**
     * 获取时区偏移量,基于指定时间
     *
     * @param zoneId
     * @param instant
     * @return
     */
    public static ZoneOffset getZoneOffset(ZoneId zoneId, Instant instant) {
        if (zoneId == null) {
            return null;
        }
        ZoneOffset offset = zoneId.getRules().getOffset(instant);
        return offset;
    }

    /**
     * 获取时区对应的偏移
     *
     * @param zoneId
     * @param time
     * @return
     */
    public static ZoneOffset getZoneOffset(ZoneId zoneId, Date time) {
        if (zoneId == null) {
            return null;
        }
        ZoneOffset offset = zoneId.getRules().getOffset(time.toInstant());
        return offset;
    }

    /**
     * 获取时区偏移量，基于最新时间
     *
     * @param timeZone
     * @return
     */
    public static ZoneOffset getZoneOffset(TimeZone timeZone) {
        return timeZone == null ? null : getZoneOffset(timeZone.toZoneId());
    }

    /**
     * 获取时区偏移量，基于最新时间
     *
     * @param timeZone
     * @return
     */
    public static ZoneOffset getZoneOffset(String timeZone) {
        ZoneId zoneId = getZoneId(timeZone);
        return zoneId == null ? null : getZoneOffset(zoneId);
    }

    /**
     * 获取系统的默认时区偏移量，基于当前时间
     *
     * @return
     */
    public static ZoneOffset getZoneOffsetDefault() {
        return getZoneOffset(ZoneOffset.systemDefault());
    }

    /**
     * 获取系统默认时区
     *
     * @return
     */
    public static ZoneId getZoneIdDefault() {
        return ZoneOffset.systemDefault();
    }

    /**
     * 获取系统的默认时区偏移量，基于历史时间
     *
     * @return
     */
    public static ZoneOffset getZoneOffsetDefault(Instant instant) {
        return getZoneOffset(ZoneOffset.systemDefault(), instant);
    }
}
