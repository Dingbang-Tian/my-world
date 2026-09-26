package com.dingbang.myworld.common.utils.desensitization.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.dingbang.myworld.common.utils.desensitization.annotation.Desensitization;
import com.dingbang.myworld.common.utils.desensitization.enums.DesensitizationType;
import com.dingbang.myworld.common.utils.desensitization.util.DesensitizationUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * 脱敏字段 JSON 序列化器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@NoArgsConstructor
@AllArgsConstructor
public class DesensitizationSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 脱敏类型
     */
    private DesensitizationType desensitizationType;

    /**
     * 前几位不脱敏
     */
    private Integer prefixNoMaskLen;

    /**
     * 后几位不脱敏
     */
    private Integer suffixNoMaskLen;

    /**
     * 用什么打码
     */
    private String symbol;



    @Override
    public void serialize(String origin, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (StringUtils.isNotEmpty(origin) && null != desensitizationType) {
            switch (desensitizationType) {
                case CUSTOMER:
                    jsonGenerator.writeString(DesensitizationUtil.desValue(origin, prefixNoMaskLen, suffixNoMaskLen, symbol));
                    break;
                case NAME:
                    jsonGenerator.writeString(DesensitizationUtil.hideChineseName(origin));
                    break;
                case ID_CARD:
                    jsonGenerator.writeString(DesensitizationUtil.hideIDCard(origin));
                    break;
                case PHONE:
                    jsonGenerator.writeString(DesensitizationUtil.hidePhone(origin));
                    break;
                case EMAIL:
                    jsonGenerator.writeString(DesensitizationUtil.hideEmail(origin));
                    break;
                default:
                    throw new IllegalArgumentException("unkown privacy enum" + desensitizationType);
            }
        } else {
            jsonGenerator.writeString(origin);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializerProvider, BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                Desensitization desensitization = beanProperty.getAnnotation(Desensitization.class);
                if (desensitization == null) {
                    desensitization = beanProperty.getContextAnnotation(Desensitization.class);
                }
                if (desensitization != null) {
                    return new DesensitizationSerializer(desensitization.type(), desensitization.prefixNoMaskLen(),
                            desensitization.suffixNoMaskLen(), desensitization.symbol());
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}
