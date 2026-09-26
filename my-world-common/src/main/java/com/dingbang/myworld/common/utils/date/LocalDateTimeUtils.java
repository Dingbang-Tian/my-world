package com.dingbang.myworld.common.utils.date;

import cn.hutool.core.date.LocalDateTimeUtil;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 本地日期时间操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class LocalDateTimeUtils extends LocalDateTimeUtil {

    public static LocalDate parseDateStrictly(String str, String... parsePatterns) throws ParseException {
        if (Objects.isNull(str) || Objects.isNull(parsePatterns)) {
            throw new IllegalArgumentException("Date and Patterns must not be null");
        }

        for (String parsePattern : parsePatterns) {
            try {
                return parseDate(str, parsePattern);
            } catch (Exception e) {
                // NOP
            }
        }

        throw new ParseException("Unable to parse the date: " + str, -1);
    }

}
