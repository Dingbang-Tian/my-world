package com.dingbang.myworld.common.utils.sensitive.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.dingbang.myworld.common.utils.ClassTypeUtils;
import com.dingbang.myworld.common.utils.lang.StringUtils;
import com.dingbang.myworld.common.utils.sensitive.util.SensitiveUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 敏感字段 JSON 序列化器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */

public class SensitiveJsonSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    /**
     * 默认 JSON 序列化器。
     */
    private static final JsonSerializer<Object> DEFAULT_SERIALIZER = new StringSerializer();

    /**
     * 默认 JSON 序列化器提供器。
     */
    private static final DefaultSerializerProvider DEFAULT_SERIALIZER_PROVIDER = new DefaultSerializerProvider.Impl();


    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (SensitiveUtil.isMatchField(property)) {
            return this;
        }
        return DEFAULT_SERIALIZER_PROVIDER.findValueSerializer(property.getType(), property);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if(Objects.isNull(value)){
            return;
        }
        if(value instanceof Collection) {
            gen.writeStartArray();
            gen.writeEndArray();
            return;
        }
        if (value instanceof Map) {
            gen.writeStartObject();
            gen.writeEndObject();
            return;
        }

        if(ClassTypeUtils.isBaseType(value.getClass())) {
            //预留一个占位符，方便靠传入来判断
            //todo 支持占位符自定义配置
            gen.writeString(StringUtils.EMPTY);
            return;
        }
        //按照对象写入一个占位符，便于排查问题
        gen.writeStartObject();
        gen.writeEndObject();

    }
}
