package com.dingbang.myworld.common.utils.desensitization.enums;

/**
 * 数据脱敏类型枚举。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public enum DesensitizationType {

    /**
     * 自定义(此项需设置脱敏范围）
     */
    CUSTOMER,

    /**
     * 姓名
     */
    NAME,

    /**
     * 身份证号
     */
    ID_CARD,

    /**
     * 手机号
     */
    PHONE,

    /**
     * 邮箱
     */
    EMAIL

}
