package com.dingbang.myworld.common.utils.converter;

import cn.hutool.core.util.BooleanUtil;
import com.dingbang.myworld.common.utils.collection.ListUtils;
import com.dingbang.myworld.common.utils.constant.DateConstants;
import com.dingbang.myworld.common.utils.constant.SymbolConstants;
import com.dingbang.myworld.common.utils.date.LocalDateTimeUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 数据类型转换工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class TypeConverter {

    /**
     * List<String> 转 String
     *
     * @param list 字符串列表
     * @return String
     */
    public static String listToString(List<String> list) {
        return StringUtils.join(SymbolConstants.COMMA, list);
    }

    /**
     * List<String> 转 String[]
     *
     * @param list 字符串列表
     * @return String[]
     */
    public static String[] listToArray(List<String> list) {
        String[] emptyArray = ArrayUtils.EMPTY_STRING_ARRAY;

        if (CollectionUtils.isEmpty(list)) {
            return emptyArray;
        }

        return list.toArray(emptyArray);
    }

    /**
     * String 转 List<String>
     *
     * @param value 字符串
     * @return List<String>
     */
    public static List<String> stringToList(String value) {
        return StringUtils.split(value, SymbolConstants.COMMA);
    }

    /**
     * String[] 转 List<String>
     *
     * @param values 字符串数组
     * @return List<String>
     */
    public static List<String> arrayToList(String[] values) {
        return ListUtils.toList(values);
    }

    /**
     * String 转 Boolean
     *
     * @param value 字符串
     * @return Boolean
     */
    public static Boolean stringToBoolean(String value) {
        return BooleanUtil.toBooleanObject(value);
    }

    /**
     * String 转 Integer
     */
    public static Integer stringToInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * String 转 Positive Integer
     */
    public static Integer stringToPositiveInteger(String value) {
        Integer result = stringToInteger(value);
        return Objects.nonNull(result) && result > 0 ? result : null;
    }

    /**
     * String 转 Non-negative Integer
     */
    public static Integer stringToPositiveOrZeroInteger(String value) {
        Integer result = stringToInteger(value);
        return Objects.nonNull(result) && result >= 0 ? result : null;
    }

    /**
     * String 转 BigDecimal
     *     注意：Scale默认2，RoundingMode默认HALF_UP
     */
    public static BigDecimal stringToBigDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            BigDecimal result = new BigDecimal(value);
            return result.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * String 转 BigDecimal
     */
    public static BigDecimal stringToOriginBigDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * String 转 Positive BigDecimal
     */
    public static BigDecimal stringToPositiveBigDecimal(String value) {
        BigDecimal result = stringToOriginBigDecimal(value);
        return Objects.nonNull(result) && BigDecimal.ZERO.compareTo(result) < 0 ? result : null;
    }

    /**
     * String 转 Non-negative BigDecimal
     */
    public static BigDecimal stringToPositiveOrZeroBigDecimal(String value) {
        BigDecimal result = stringToOriginBigDecimal(value);
        return Objects.nonNull(result) && BigDecimal.ZERO.compareTo(result) <= 0 ? result : null;
    }

    /**
     * String 转 LocalDate
     *    注意：按常用日期格式列表转换 {@link DateConstants#FREQUENT_DATE_PATTERNS}
     */
    public static LocalDate stringToLocalDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return LocalDateTimeUtils.parseDateStrictly(value, DateConstants.FREQUENT_DATE_PATTERNS);
        } catch (ParseException e) {
            return null;
        }
    }

}
