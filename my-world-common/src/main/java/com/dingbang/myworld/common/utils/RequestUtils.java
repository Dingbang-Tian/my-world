package com.dingbang.myworld.common.utils;

import cn.hutool.core.util.StrUtil;
import com.dingbang.myworld.common.exception.BusinessException;
import com.dingbang.myworld.common.exception.ErrorCode;
import com.dingbang.myworld.common.utils.context.OperUser;
import com.dingbang.myworld.common.utils.context.OperationSource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 请求上下文管理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class RequestUtils {

    /**
     * 租户 header
     */
    public static final String HEADER_TENANT = "X-LK-Tenant";

    /**
     * 时区 header
     */
    public static final String HEADER_TIMEZONE = "X-LK-TIMEZONE";

    /**
     * 语言 header
     */
    public static final String HEADER_LANGUAGE = "Accept-Language";

    /**
     * 渠道 header
     */
    public static final String HEADER_CID = "X-LK-CID";

    /**
     * 请求里存放登录用户的 attribute
     */
    public static final String LOGIN_USER = "loginUser";

    /**
     * 请求里存放菜单权限的 attribute
     */
    public static final String MENU_PERMISSIONS = "menuPermissions";

    /**
     * 系统来源标记
     */
    public static final String SYSTEM_FLAG = "system";

    /**
     * 忽略租户时使用的租户值
     */
    public static final String TENANT_ALL = "ALL";

    private static final ThreadLocal<Map<String, String>> ATTRIBUTES = ThreadLocal.withInitial(HashMap::new);

    private static final ThreadLocal<OperUser> CUSTOM_USER = new ThreadLocal<>();

    /**
     * 当前登录用户。优先自定义用户，其次请求 attribute 中的登录用户。
     *
     * @return user
     */
    public static OperUser getSessionUser() {
        return getSessionUser(false);
    }

    /**
     * 读取请求中的登录用户
     *
     * @param ignoreError
     * @return user
     */
    public static OperUser getSessionUser(boolean ignoreError) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            if (!ignoreError) {
                log.warn("current request missing, return empty user");
            }
            return new OperUser();
        }
        Object loginUser = request.getAttribute(LOGIN_USER);
        if (loginUser instanceof OperUser operUser) {
            return operUser;
        }
        if (!ignoreError) {
            log.warn("login user attribute missing");
        }
        return new OperUser();
    }

    /**
     * 当前菜单权限。由登录过滤器写入请求 attribute。
     *
     * @return permissions
     */
    public static Set<String> getMenuPermissionList() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return new HashSet<>();
        }
        Object permissions = request.getAttribute(MENU_PERMISSIONS);
        if (permissions instanceof Collection<?> collection) {
            Set<String> result = new HashSet<>();
            for (Object item : collection) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return new HashSet<>();
    }

    /**
     * 设置当前线程的自定义用户
     *
     * @param operUser
     */
    public static void setCustomUser(OperUser operUser) {
        if (operUser == null) {
            CUSTOM_USER.remove();
            return;
        }
        CUSTOM_USER.set(operUser);
        log.debug("set custom user, empNo={}", operUser.getEmpNo());
    }

    /**
     * 清除自定义用户
     */
    public static void clearCustomUser() {
        CUSTOM_USER.remove();
    }

    /**
     * 当前用户。自定义用户优先于请求登录用户。
     *
     * @return user
     */
    public static OperUser currentUser() {
        return currentUser(false);
    }

    /**
     * 当前用户
     *
     * @param ignoreError
     * @return user
     */
    public static OperUser currentUser(boolean ignoreError) {
        OperUser customUser = getCustomUser();
        if (customUser != null) {
            return customUser;
        }
        return getSessionUser(ignoreError);
    }

    /**
     * 只返回手动设置的用户
     *
     * @return user
     */
    @Deprecated
    public static OperUser getCurrentUserFromCustomFlag() {
        return getCustomUser();
    }

    /**
     * 把当前用户标成系统用户。调用方需要自己清理，优先使用 {@link #runWithSystemSourceFlag(Runnable)}。
     */
    @Deprecated
    public static void setSystemSourceFlag() {
        setCustomUser(OperUser.system());
    }

    /**
     * 以系统用户执行
     *
     * @param runnable
     */
    public static void runWithSystemSourceFlag(Runnable runnable) {
        runWithCustomUser(OperUser.system(), runnable);
    }

    /**
     * 设置外部来源用户
     *
     * @param operUser
     */
    public static void setExternalSourceUser(OperUser operUser) {
        setCustomUser(operUser);
    }

    /**
     * 当前租户。线程属性优先，其次请求头。都没有时抛出业务异常。
     *
     * @return tenant
     */
    public static String currentTenant() {
        String tenant = attribute(HEADER_TENANT);
        if (StringUtils.isNotBlank(tenant)) {
            return tenant;
        }
        return getHeader(HEADER_TENANT)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "lost tenant"));
    }

    /**
     * 当前时区。请求头优先，其次线程属性。
     *
     * @return timeZone
     */
    public static String currentTimeZone() {
        String timeZone = getHeader(HEADER_TIMEZONE).orElse("");
        if (StringUtils.isBlank(timeZone)) {
            return attribute(HEADER_TIMEZONE);
        }
        return timeZone;
    }

    /**
     * 当前语言。请求头优先，其次线程属性。
     *
     * @return language
     */
    public static String currentLanguage() {
        String languageCode = getHeader(HEADER_LANGUAGE).orElse("");
        if (StringUtils.isBlank(languageCode)) {
            return attribute(HEADER_LANGUAGE);
        }
        return languageCode;
    }

    /**
     * 线程里的语言。没有时回退到 {@link #currentLanguage()}。
     *
     * @return language
     */
    public static String contextLanguage() {
        String languageCode = attribute(HEADER_LANGUAGE);
        if (StringUtils.isEmpty(languageCode)) {
            return currentLanguage();
        }
        return languageCode;
    }

    /**
     * 当前请求
     *
     * @return request
     */
    public static HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * 渠道号，取 header 前三位
     *
     * @return cid
     */
    public static Integer currentCid() {
        String cid = attribute(HEADER_CID);
        if (StringUtils.isBlank(cid)) {
            cid = getHeader(HEADER_CID).orElse("");
        }
        if (StringUtils.isBlank(cid) || cid.length() < 3) {
            return OperationSource.SYSTEM.getCode();
        }
        return Integer.valueOf(cid.substring(0, 3));
    }

    /**
     * 读取请求头
     *
     * @param headerName
     * @return header
     */
    public static Optional<String> getHeader(String headerName) {
        return Optional.ofNullable(currentRequest())
                .map(req -> req.getHeader(headerName))
                .filter(StringUtils::isNotEmpty);
    }

    /**
     * 按来源取用户。系统来源返回内置系统用户。
     *
     * @param operationSource
     * @return user
     */
    public static OperUser currentUser(OperationSource operationSource) {
        if (operationSource == OperationSource.SYSTEM) {
            return OperUser.system();
        }
        return currentUser();
    }

    /**
     * 在指定语言中执行，并在结束后恢复 Locale
     *
     * @param languageCode
     * @param supplier
     * @return value
     */
    public static <V> V callWithLanguage(@NonNull String languageCode, @NonNull Supplier<V> supplier) {
        Locale oldLocale = LocaleContextHolder.getLocale();
        if (languageCode.contains(StrUtil.DASHED)) {
            String[] languageArray = languageCode.split(StrUtil.DASHED);
            LocaleContextHolder.setLocale(new Locale(languageArray[0], languageArray[1]));
        } else {
            LocaleContextHolder.setLocale(new Locale(languageCode));
        }
        try {
            return callWithAttribute(HEADER_LANGUAGE, languageCode, supplier);
        } finally {
            LocaleContextHolder.setLocale(oldLocale);
        }
    }

    /**
     * 在指定租户中执行
     *
     * @param tenant
     * @param supplier
     * @return value
     */
    public static <V> V callWithTenant(@NonNull String tenant, @NonNull Supplier<V> supplier) {
        return callWithAttribute(HEADER_TENANT, tenant, supplier);
    }

    /**
     * 在指定租户中执行
     *
     * @param tenant
     * @param runnable
     */
    public static void runWithTenant(@NonNull String tenant, @NonNull Runnable runnable) {
        callWithTenant(tenant, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 以指定用户执行，结束后恢复原来的自定义用户
     *
     * @param customUser
     * @param runnable
     */
    public static void runWithCustomUser(@NonNull OperUser customUser, @NonNull Runnable runnable) {
        callWithCustomUser(customUser, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 以全部租户执行
     *
     * @param supplier
     * @return value
     */
    public static <V> V callWithoutTenant(@NonNull Supplier<V> supplier) {
        return callWithTenant(TENANT_ALL, supplier);
    }

    /**
     * 以全部租户执行
     *
     * @param runnable
     */
    public static void runWithoutTenant(@NonNull Runnable runnable) {
        callWithTenant(TENANT_ALL, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 临时写入线程属性，结束后恢复
     *
     * @param attrName
     * @param attrValue
     * @param supplier
     * @return value
     */
    public static <V> V callWithAttribute(@NonNull String attrName, @NonNull String attrValue, @NonNull Supplier<V> supplier) {
        Map<String, String> attributes = ATTRIBUTES.get();
        String currentAttrValue = attributes.get(attrName);
        try {
            attributes.put(attrName, attrValue);
            return supplier.get();
        } finally {
            if (currentAttrValue == null) {
                attributes.remove(attrName);
            } else {
                attributes.put(attrName, currentAttrValue);
            }
        }
    }

    /**
     * 以指定用户执行
     *
     * @param customUser
     * @param supplier
     * @return value
     */
    public static <V> V callWithCustomUser(@NonNull OperUser customUser, @NonNull Supplier<V> supplier) {
        OperUser currentUser = getCustomUser();
        setCustomUser(customUser);
        try {
            return supplier.get();
        } finally {
            if (currentUser == null) {
                clearCustomUser();
            } else {
                setCustomUser(currentUser);
            }
        }
    }

    /**
     * 带租户、语言、用户执行
     *
     * @param tenant
     * @param languageCode
     * @param customUser
     * @param runnable
     */
    public static void runWith(@NonNull String tenant, @NonNull String languageCode, @NonNull OperUser customUser,
                               @NonNull Runnable runnable) {
        callWith(tenant, languageCode, customUser, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 带租户、语言、用户执行
     *
     * @param tenant
     * @param languageCode
     * @param customUser
     * @param supplier
     * @return value
     */
    public static <V> V callWith(@NonNull String tenant, @NonNull String languageCode, @NonNull OperUser customUser,
                                 @NonNull Supplier<V> supplier) {
        return callWithTenant(tenant, () -> callWithLanguage(languageCode, () -> callWithCustomUser(customUser, supplier)));
    }

    /**
     * 当前线程上的上下文属性，给切面日志使用
     *
     * @return attributes
     */
    public static Map<String, String> snapshotAttributes() {
        return new HashMap<>(ATTRIBUTES.get());
    }

    private static OperUser getCustomUser() {
        return CUSTOM_USER.get();
    }

    private static String attribute(String name) {
        return ATTRIBUTES.get().get(name);
    }
}
