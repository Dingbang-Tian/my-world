package com.dingbang.myworld.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * SpEL 表达式处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class SpelUtils {

    private static ExpressionParser parser = new SpelExpressionParser();
    private static final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析spel表达式
     * @param method 方法
     * @param arguments 参数
     * @param spel 表达式
     * @return 解析结果
     */
    public static String parseSpel(Method method, Object[] arguments, String spel) {
        if (StringUtils.isBlank(spel)) {
            return "";
        }
        String[] params = discoverer.getParameterNames(method);
        EvaluationContext context = new StandardEvaluationContext();
        if (params != null) {
            for (int len = 0; len < params.length; len++) {
                context.setVariable(params[len], arguments[len]);
            }
        }
        Expression expression = parser.parseExpression(spel);
        return expression.getValue(context, String.class);
    }

    public static String parseSpel(JoinPoint point, String spelText) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        return SpelUtils.parseSpel(signature.getMethod(), point.getArgs(), spelText);
    }
}
