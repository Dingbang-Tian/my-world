package com.dingbang.myworld.common.utils.sensitive.serializer;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.dingbang.myworld.common.utils.sensitive.util.SensitiveUtil;

import java.util.List;

/**
 * 敏感字段序列化修改器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class SensitiveJsonSerializerModifier extends BeanSerializerModifier {

    private final SensitiveJsonSerializer sensitiveJsonSerializer = new SensitiveJsonSerializer();

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter beanProperty : beanProperties) {
            if(SensitiveUtil.isMatchField(beanProperty)){
                beanProperty.assignSerializer(sensitiveJsonSerializer);
            }
        }
        return beanProperties;
    }

}
