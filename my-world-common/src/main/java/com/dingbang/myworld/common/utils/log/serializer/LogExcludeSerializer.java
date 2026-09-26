package com.dingbang.myworld.common.utils.log.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.dingbang.myworld.common.utils.log.LogExclude;

import java.io.IOException;

/**
 * 日志排除字段序列化器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class LogExcludeSerializer extends JsonSerializer<Object> {

    private final String placeholder;

    public LogExcludeSerializer(String placeholder) {
        this.placeholder = placeholder == null ? "[omitted]" : placeholder;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(placeholder);
    }
}
