package com.dingbang.myworld.common.utils.desensitization.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 敏感数据脱敏工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class DesensitizationUtil {

    public static final String PREFIX_PLUS = "+";

    /**
     * 隐藏手机号中间四位
     */
    public static String hidePhone(String phone) {
        int minLen = 4;
        int needHideMinLen = 8;
        String areaCode = StringUtils.EMPTY;
        if (StringUtils.isNotEmpty(phone) && phone.startsWith(PREFIX_PLUS)) {
            areaCode = phone.substring(0, 3);
            phone = phone.substring(3);
        }
        //四位以下不处理
        if (StringUtils.isBlank(phone) || phone.length() <= minLen) {
            return phone;
        }

        int phoneLength = phone.length();
        String result;
        if (phoneLength <= needHideMinLen) {
            int hideLength = phoneLength - minLen - 1;
            StringBuilder replacement = new StringBuilder();
            replacement.append("$1");
            for (int i = 0; i < hideLength; i++) {
                replacement.append("*");
            }
            replacement.append("$2");
            result = phone.replaceAll("(\\d{1})\\d{" + hideLength + "}(\\d{" + minLen + "})", replacement.toString());
        } else {
            result = phone.replaceAll("(\\d{" + (phoneLength - needHideMinLen) + "})\\d{4}(\\d{4})", "$1****$2");
        }
        if (StringUtils.isNotEmpty(areaCode)) {
            result = areaCode + result;
        }
        return result;
    }

    /**
     * 隐藏邮箱
     */
    public static String hideEmail(String email) {
        return email.replaceAll("(\\w?)(\\w+)(\\w)(@\\w+\\.[a-z]+(\\.[a-z]+)?)", "$1****$3$4");
    }

    /**
     * 隐藏身份证
     */
    public static String hideIDCard(String idCard) {
        return idCard.replaceAll("(\\d{4})\\d{10}(\\w{4})", "$1*****$2");
    }

    /**
     * 【中文姓名】只显示第一个汉字，其他隐藏为星号，比如：任**
     */
    public static String hideChineseName(String chineseName) {
        if (chineseName == null) {
            return null;
        }
        return desValue(chineseName, 1, 0, "*");
    }

    /**
     * 对字符串进行脱敏操作
     *
     * @param origin          原始字符串
     * @param prefixNoMaskLen 左侧需要保留几位明文字段
     * @param suffixNoMaskLen 右侧需要保留几位明文字段
     * @param maskStr         用于遮罩的字符串, 如'*'
     * @return 脱敏后结果
     */
    public static String desValue(String origin, int prefixNoMaskLen, int suffixNoMaskLen, String maskStr) {
        if (origin == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = origin.length(); i < n; i++) {
            if (i < prefixNoMaskLen) {
                sb.append(origin.charAt(i));
                continue;
            }
            if (i > (n - suffixNoMaskLen - 1)) {
                sb.append(origin.charAt(i));
                continue;
            }
            sb.append(maskStr);
        }
        return sb.toString();
    }
}
