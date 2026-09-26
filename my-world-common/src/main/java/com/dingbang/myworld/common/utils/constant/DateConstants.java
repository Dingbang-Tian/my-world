package com.dingbang.myworld.common.utils.constant;

import org.apache.commons.lang3.ArrayUtils;

/**
 * 日期格式常量接口。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public interface DateConstants {

    /**
     * yyyy-MM-dd HH:mm:ss
     */
    String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * yyyyMMddHHmmss
     */
    String DATE_TIME_SIMPLE_FORMAT = "yyyyMMddHHmmss";

    /**
     * yyyy-MM-dd HH:mm:ss:SSS
     */
    String DATE_TIME_MS_FORMAT = "yyyy-MM-dd HH:mm:ss:SSS";

    /**
     * yyyy-MM-dd
     */
    String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * yyyyMMdd
     */
    String DATE_SIMPLE_FORMAT = "yyyyMMdd";

    /**
     * yyyy/MM/dd
     */
    String DATE_LINE_FORMAT = "yyyy/MM/dd";

    /**
     * M/d/yy
     */
    String DATE_SHORT_FORMAT = "M/d/yy";

    /**
     * yyyy年MM月dd日
     */
    String DATE_CN_FORMAT = "yyyy年MM月dd日";

    /**
     * yyyy-MM
     */
    String MONTH_FORMAT = "yyyy-MM";

    /**
     * yyyyMM
     */
    String MONTH_SIMPLE_FORMAT = "yyyyMM";

    /**
     * yyyy/MM
     */
    String MONTH_LINE_FORMAT = "yyyy/MM";

    /**
     * yyyy年MM月
     */
    String MONTH_CN_FORMAT = "yyyy年MM月";

    /**
     * yyyy年
     */
    String YEAR_CN_FORMAT = "yyyy年";

    /**
     * HH:mm:ss
     */
    String TIME_FORMAT = "HH:mm:ss";

    /**
     * HHmmss
     */
    String TIME_SIMPLE_FORMAT = "HHmmss";

    /**
     * 00:00:00
     */
    String DAY_START = "00:00:00";

    /**
     * 23:59:59
     */
    String DAY_END = "23:59:59";

    /**
     * RFC3339
     */
    String RFC3339 = "yyyy-MM-dd'T'HH:mm:ssXXX";
    /**
     * 常用日期格式
     *     注意：需要使用严格模式解析，否则会导致误判
     */
    String[] FREQUENT_DATE_PATTERNS = ArrayUtils.toArray(
        DATE_SIMPLE_FORMAT, DATE_SHORT_FORMAT, DATE_LINE_FORMAT, DATE_FORMAT, DATE_CN_FORMAT
    );

    /**
     * 月份正则（前面不能加0）
     */
    String MONTH_REGEX = "^[1-9]$|^1[0-2]$";

    /**
     * 月份正则
     */
    String MONTH_REGEX_LOOSE = "^0?[1-9]$|^1[0-2]$";
    /**
     * 年份正则（4位数字）
     */
    String YEAR_REGEX = "^[1-9]\\d{3}$";

}
