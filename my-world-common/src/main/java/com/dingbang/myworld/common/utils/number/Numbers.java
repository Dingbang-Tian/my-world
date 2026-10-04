package com.dingbang.myworld.common.utils.number;

import cn.hutool.core.util.StrUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.dingbang.myworld.common.utils.lang.CompareUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.springframework.lang.Nullable;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 数值操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Numbers {


    /**
     * 金额的小数精度。
     */
    public static final int AMOUNT_SCALE = FitNums.INT_2;

    /**
     * 价格的小数精度。
     */
    public static final int PRICE_SCALE = FitNums.INT_4;

    /**
     * 数值一百。
     */
    public static final BigDecimal NUM_100 = new BigDecimal("100");
    /**
     * 数值一千。
     */
    public static final BigDecimal NUM_1000 = new BigDecimal("1000");

    /**
     * 转 BigDecimal(空值转0)
     *
     * @param arg
     * @return
     */
    @NotNull
    public static BigDecimal of(Object arg) {
        return newBigDecimal(arg, true, false);
    }

    /**
     * 转 BigDecimal(空值、负数转0)
     *
     * @param arg
     * @return
     */
    @NotNull
    public static BigDecimal ofNonNegative(Object arg) {
        return newBigDecimal(arg, true, true);
    }

    /**
     * 转 BigDecimal(空值、负数不处理)
     *
     * @param obj
     * @return
     */
    @Nullable
    public static BigDecimal ofNullable(Object obj) {
        return newBigDecimal(obj, false, false);
    }

    /**
     * negate
     *
     * @param arg
     * @return
     * @see BigDecimal#negate()
     */
    public static BigDecimal negate(Object arg) {
        return of(arg).negate();
    }

    /**
     * 设置Scale
     * 默认：RoundingMode = HALF_UP
     */
    public static BigDecimal halfUpScale(BigDecimal target, int scale) {
        if (Objects.isNull(target)) {
            return null;
        }

        return target.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 除百分数
     * 默认：RoundingMode = HALF_UP
     */
    public static BigDecimal halfUpDivideByPercent(BigDecimal dividend, int scale) {
        return halfUpDivide(dividend, NUM_100, scale);
    }

    /**
     * 相除
     * 默认：RoundingMode = HALF_UP
     */
    public static BigDecimal halfUpDivide(BigDecimal dividend, BigDecimal divisor, int scale) {
        if (Objects.isNull(dividend)) {
            return null;
        }

        return dividend.divide(divisor, scale, RoundingMode.HALF_UP);
    }

    /**
     * 计算比率原始数值
     * 默认：Scale = 4
     */
    public static BigDecimal calculateRateOriginalValue(BigDecimal rateValue, BigDecimal rate) {
        // 比率计算公式：比率数值 = (1 + 比率%) * 比率原始数值
        BigDecimal divisor = Numbers.add(FitNums.INT_1, Numbers.halfUpDivideByPercent(rate, FitNums.INT_4));
        return Numbers.halfUpDivide(rateValue, divisor, FitNums.INT_4);
    }

    /**
     * 累加
     */
    public static BigDecimal add(Object... args) {
        return reduce(BigDecimal.ZERO, BigDecimal::add, args);
    }

    /**
     * 累加(返回结果>=0)
     */
    public static BigDecimal addNonNegative(Object... args) {
        return add(args).max(BigDecimal.ZERO);
    }

    /**
     * 累减(obj[0]-obj[1]-...)
     */
    public static BigDecimal subtract(Object... args) {
        return reduce(null, BigDecimal::subtract, args);
    }

    /**
     * 累减(返回结果>=0)
     */
    public static BigDecimal subtractNonNegative(Object... obj) {
        return subtract(obj).max(BigDecimal.ZERO);
    }

    /**
     * 是否<0
     */
    public static boolean isNegative(Object obj) {
        return of(obj).signum() < 0;
    }

    /**
     * 是否<=0
     */
    public static boolean isNegativeOrZero(Object obj) {
        return of(obj).signum() <= 0;
    }

    /**
     * 是否>0
     */
    public static boolean isPositive(Object obj) {
        return of(obj).signum() > 0;
    }

    /**
     * 是否>=0
     */
    public static boolean isPositiveOrZero(Object obj) {
        return of(obj).signum() >= 0;
    }

    /**
     * 是否整数
     */
    public static boolean isInt(Object obj) {
        return of(obj).stripTrailingZeros().scale() <= 0;
    }

    /**
     * 非负整数
     * @param obj
     * @return
     */
    public static boolean isPositiveOrZeroInt(Object obj) {
        BigDecimal val = of(obj);
        return isInt(val) && isPositiveOrZero(val);
    }

    /**
     * 正整数
     * @param obj
     * @return
     */
    public static boolean isPositiveInt(Object obj) {
        BigDecimal val = of(obj);
        return isInt(val) && isPositive(val);
    }

    /**
     * 是否==0
     */
    public static boolean isZero(Object obj) {
        return of(obj).signum() == 0;
    }
    /**
     * 是否==0
     */
    public static boolean isNoneZero(Object obj) {
        return !isZero(obj);
    }

    /**
     * 是否相等
     */
    public static boolean isEqual(Object obj, Object obj2) {
        return of(obj).compareTo(of(obj2)) == 0;
    }

    /**
     * 累乘
     *
     * @param obj
     * @return
     */
    public static BigDecimal multiply(Object... obj) {
        return reduce(BigDecimal.ZERO, BigDecimal::multiply, obj);
    }

    /**
     * 累乘（返回结果>=0）
     *
     * @param obj
     * @return
     */
    public static BigDecimal multiplyNonNegative(Object... obj) {
        return multiply(obj).max(BigDecimal.ZERO);
    }

    /**
     * 区间判断
     *
     * @param min    最小值(包含)
     * @param middle 中间值
     * @param max    最大值(包含)
     * @param <N>
     * @return
     */
    public static <N extends Number & Comparable<? super N>> boolean between(N min, N middle, N max) {
        return CompareUtils.between(min, middle, max);
    }

    /**
     * 区间判断
     *
     * @param min        最小值
     * @param middle     中间值
     * @param max        最大值
     * @param containMin 包含最小值
     * @param containMax 包含最大值
     * @param <N>
     * @return
     */
    public static <N extends Number & Comparable<? super N>> boolean between(N min, N middle, N max, boolean containMin, boolean containMax) {
        return CompareUtils.between(min, middle, max, containMin, containMax);
    }

    /**
     * 转 BigDecimal
     *
     * @param obj            参数
     * @param nullToZero     空值转0
     * @param negativeToZero 负数转0
     * @return
     */
    public static BigDecimal newBigDecimal(Object obj, boolean nullToZero, boolean negativeToZero) {
        if (obj == null) {
            return nullToZero ? BigDecimal.ZERO : null;
        }
        BigDecimal val = obj instanceof BigDecimal ? (BigDecimal) obj : new BigDecimal(obj.toString());
        return negativeToZero ? val.max(BigDecimal.ZERO) : val;
    }

    /**
     * 计算
     *
     * @param def      默认值
     * @param operator 操作
     * @param args     参数
     * @return
     */
    private static BigDecimal reduce(BigDecimal def, BinaryOperator<BigDecimal> operator, Object... args) {
        if (args == null || args.length == 0) {
            return BigDecimal.ZERO;
        }
        switch (args.length) {
            case 1:
                return of(args[0]);
            case 2:
                return operator.apply(of(args[0]), of(args[1]));
            default:
                if (def == null) {
                    return Arrays.stream(args).skip(1).map(Numbers::ofNullable).filter(Objects::nonNull).reduce(of(args[0]), operator);
                }
                return Arrays.stream(args).map(Numbers::ofNullable).filter(Objects::nonNull).reduce(operator).orElse(def);
        }
    }

    /**
     * 按目标类型索引的数字转换函数。
     */
    private static final Map<Class, Function<Number, Number>> CONVERT_FUNC = ((Supplier<Map<Class, Function<Number, Number>>>) () -> {
        Map<Class, Function<Number, Number>> func = Maps.newHashMap();
        func.put(Integer.class, Number::intValue);
        func.put(Long.class, Number::longValue);
        func.put(Float.class, Number::floatValue);
        func.put(Double.class, Number::doubleValue);
        func.put(Byte.class, Number::byteValue);
        func.put(Short.class, Number::shortValue);
        return func;
    }).get();

    /**
     * 数值类型转换
     *
     * @param obj   参数
     * @param clazz 包装类
     * @param <N>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <N extends Number> N convert(Object obj, Class<N> clazz) {
        if (obj == null || clazz == null) {
            return null;
        }
        if (obj.getClass() == clazz) {
            return (N) obj;
        }
        if (clazz == BigDecimal.class) {
            return (N) of(obj);
        }
        Function<Number, Number> func = CONVERT_FUNC.get(clazz);
        Preconditions.checkNotNull(func, "not convert function for class " + clazz);
        return (N) func.apply(obj instanceof Number ? (Number) obj : of(obj));
    }

    public static BigDecimal min(Object... numbers) {
        if (numbers.length == 0) {
            return BigDecimal.ZERO;
        }
        return Arrays.stream(numbers).filter(Objects::nonNull).map(Numbers::of).min(BigDecimal::compareTo).orElse(null);
    }

    public static BigDecimal max(Object... numbers) {
        if (numbers.length == 0) {
            return BigDecimal.ZERO;
        }
        return Arrays.stream(numbers).filter(Objects::nonNull).map(Numbers::of).max(BigDecimal::compareTo).orElse(null);
    }

    /**
     * 转为字符串
     *
     * @param value
     * @param nullToZero
     * @return
     * @see BigDecimal#stripTrailingZeros()
     * @see BigDecimal#toPlainString()
     */
    public static String toStr(Object value, boolean nullToZero) {
        if (value == null) {
            return nullToZero ? "0" : StrUtil.EMPTY;
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        return Numbers.of(value).stripTrailingZeros().toPlainString();
    }

    public static String toStr(Object value) {
        return toStr(value, true);
    }

    /**
     * 金额格式化（四舍五入、保留2各位小数）
     *
     * @param amount
     * @return
     */
    public static BigDecimal formatAmount(Object amount) {
        if (amount == null) {
            return null;
        }
        return of(amount).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
    /**
     * 单价格式化（四舍五入、保留4各位小数）
     *
     * @param price
     * @return
     */
    public static BigDecimal formatPrice(Object price) {
        if (price == null) {
            return null;
        }
        return of(price).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }


    /**
     * 是否<0
     * @deprecated use {@link #isNegative(Object)}
     */
    public static boolean lt0(Object obj) {
        return isNegative(obj);
    }

    /**
     * 是否<=0
     * @deprecated use {@link #isNegativeOrZero(Object)}
     */
    public static boolean le0(Object obj) {
        return isNegativeOrZero(obj);
    }

    /**
     * 是否>0
     * @deprecated use {@link #isPositive(Object)}
     */
    public static boolean gt0(Object obj) {
        return isPositive(obj);
    }

    /**
     * 是否>=0
     * @deprecated use {@link #isPositiveOrZero(Object)}
     */
    public static boolean ge0(Object obj) {
        return isPositiveOrZero(obj);
    }

    /**
     * 是否==0
     * @deprecated use {@link #isZero(Object)}
     */
    public static boolean eq0(Object obj) {
        return isZero(obj);
    }

    /**
     * 是否相等
     * @deprecated use {@link #isEqual(Object, Object)}
     */
    public static boolean eq(Object obj, Object obj2) {
        return isEqual(obj, obj2);
    }

    /**
     * 累乘（返回结果>=0）
     * @deprecated use {@link #multiplyNonNegative(Object...)}
     * @param obj
     * @return
     */
    public static BigDecimal multiply0(Object... obj) {
        return multiply(obj).max(BigDecimal.ZERO);
    }

    /**
     * 转 BigDecimal(空值、负数转0)
     * @deprecated use {@link #ofNonNegative(Object)}
     *
     * @param arg
     * @return
     */
    public static BigDecimal of0(Object arg) {
        return newBigDecimal(arg, true, true);
    }

    /**
     * 累加(返回结果>=0)
     * @deprecated use {@link #addNonNegative(Object...)}
     */
    public static BigDecimal add0(Object... args) {
        return addNonNegative(args);
    }

    /**
     * 累减(返回结果>=0)
     * @deprecated use {@link #subtractNonNegative(Object...)}
     */
    public static BigDecimal subtract0(Object... obj) {
        return subtractNonNegative(obj);
    }

}
