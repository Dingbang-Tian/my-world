package com.dingbang.myworld.common.utils;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.Arrays;
import java.util.Map;

/**
 * AOP 代理与目标对象处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class AopUtils {
    /**
     * 获取输入参数的map集合
     * @param joinPoint 切片参数
     * @return 输入参数
     */
    public static Map<String, Object> getParamMap(JoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature)joinPoint.getSignature();
        Map<String, Object> map = Maps.newHashMap();
        String[] paramNames = methodSignature.getParameterNames();
        if (paramNames == null) {
            return Maps.newHashMap();
        }
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            map.put(paramNames[i], args[i]);
        }
        return map;
    }


    /**
     * 错误日志格式模板。
     */
    private static final String ERROR_LOG_TMPL = "{} service:[{}], method [{}], param:{}, error:{},header:{}";

    /**
     * 普通日志格式模板。
     */
    private static final String INFO_LOG_TMPL = "{} service:[{}], method [{}] ,param:{},result:{},time:{},header:{}";

    /**
     * 打印环绕日志
     *
     * @param point
     * @param errorPrefix
     * @param normalPrefix
     * @return
     * @throws Throwable
     */
    public static Object logAround(ProceedingJoinPoint point, String errorPrefix, String normalPrefix) throws Throwable {
        Object result = null;
        Object[] params = null;
        String methodName = null;
        String className = null;
        Throwable e1 = null;
        StopWatch sw = new StopWatch();
        try {
            try {
                className = point.getTarget().getClass().getSimpleName();
                params = point.getArgs();
                params = filterParams(params);
                methodName = point.getSignature().getName();
            } catch (Throwable e) {
                log.error("提取服务信息记录日志异常，{}.{}", className, methodName, e);
            }
            sw.start();
            //执行方法
            result = point.proceed();
        } catch (Throwable e) {
            e1 = e;
            throw e;
        } finally {
            sw.stop();
            if (e1 != null) {
                log.error(ERROR_LOG_TMPL, errorPrefix, className, methodName, JsonUtils.toShortJSONString(params), e1.getMessage(),
                        RequestUtils.snapshotAttributes(), e1);
            } else if (log.isInfoEnabled()) {
                log.info(INFO_LOG_TMPL, normalPrefix, className, methodName, JsonUtils.toShortJSONString(params), JsonUtils.toShortJSONString(result),
                        sw.getTime(), RequestUtils.snapshotAttributes());
            }
        }
        return result;
    }

    private static Object[] filterParams(Object[] params) {
        if (params != null) {
            params = Arrays.stream(params).filter(x -> !(x instanceof ServletRequest || x instanceof ServletResponse
                    || x instanceof MultipartFile)).toArray();
        }
        return params;
    }
}
