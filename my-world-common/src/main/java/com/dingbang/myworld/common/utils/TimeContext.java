package com.dingbang.myworld.common.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间上下文管理类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
@Slf4j
public class TimeContext {

    private ZoneId zoneId;
    private LocalDateTime now;
    private LocalDateTime startOfTheDay;
    private LocalDateTime startOfTheWeek;
    private LocalDateTime startOfTheMonth;
    private LocalDateTime endOfTheDay;
    private LocalDateTime endOfTheWeek;
    private LocalDateTime endOfTheMonth;

    public static TimeContext getTimeContext() {
        return getTimeContext(ZoneId.systemDefault().toString());
    }

    public static TimeContext getTimeContext(String zoneIdStr) {
        if (StringUtils.isBlank(zoneIdStr)) {
            return getTimeContext();
        }
        ZoneId zoneId = ZoneId.of(zoneIdStr);
        TimeContext timeContext = new TimeContext();
        LocalDateTime now = LocalDateTime.now(zoneId);
        timeContext.setNow(now);
        timeContext.setZoneId(zoneId);
        timeContext.setStartOfTheDay(ZonedDateTimeUtils.startOfTheDay(now));
        timeContext.setStartOfTheWeek(ZonedDateTimeUtils.startOfTheWeek(now));
        timeContext.setStartOfTheMonth(ZonedDateTimeUtils.startOfTheMonth(now));
        timeContext.setEndOfTheDay(ZonedDateTimeUtils.endOfTheDay(now));
        timeContext.setEndOfTheWeek(ZonedDateTimeUtils.endOfTheWeek(now));
        timeContext.setEndOfTheMonth(ZonedDateTimeUtils.endOfTheMonth(now));
        return timeContext;
    }

    /**
     * 转换时区
     * @param toZoneIdStr 转换目标时区
     * @return 目标时区数据
     */
    public TimeContext toZoneId(String toZoneIdStr) {
        ZoneId toZoneId = ZoneId.of(toZoneIdStr);
        if (toZoneId.equals(this.zoneId)) {
            return this;
        }
        TimeContext timeContext = new TimeContext();
        timeContext.setZoneId(toZoneId);
        timeContext.setNow(ZonedDateTimeUtils.convertTimeZone(this.now, this.zoneId, toZoneId));
        timeContext.setStartOfTheDay(ZonedDateTimeUtils.convertTimeZone(this.startOfTheDay, this.zoneId, toZoneId));
        timeContext.setStartOfTheWeek(ZonedDateTimeUtils.convertTimeZone(this.startOfTheWeek, this.zoneId, toZoneId));
        timeContext.setStartOfTheMonth(ZonedDateTimeUtils.convertTimeZone(this.startOfTheMonth, this.zoneId, toZoneId));
        timeContext.setEndOfTheDay(ZonedDateTimeUtils.convertTimeZone(this.endOfTheDay, this.zoneId, toZoneId));
        timeContext.setEndOfTheWeek(ZonedDateTimeUtils.convertTimeZone(this.endOfTheWeek, this.zoneId, toZoneId));
        timeContext.setEndOfTheMonth(ZonedDateTimeUtils.convertTimeZone(this.endOfTheMonth, this.zoneId, toZoneId));
        log.info("时区转换成功，源时区数据:{}，目标时区数据:{}", this, timeContext);
        return timeContext;
    }

    /**
     * 先获取fromZoneIdStr的时间段，再转换成目标时区
     * @param fromZoneIdStr 转换源时区
     * @param toZoneIdStr 转换目标时区
     * @return 目标时区数据
     */
    public static TimeContext getTimeContext(String fromZoneIdStr, String toZoneIdStr) {
        TimeContext fromTimeContext = TimeContext.getTimeContext(fromZoneIdStr);
        return fromTimeContext.toZoneId(toZoneIdStr);
    }

    /**
     * 先获取fromZoneIdStr的时间段，再转换成系统默认时区
     * @param fromZoneIdStr 转换源时区
     * @return 目标时区数据
     */
    public static TimeContext getSystemZoneTimeContext(String fromZoneIdStr) {
        return getTimeContext(fromZoneIdStr, ZoneId.systemDefault().getId());
    }
}
