package com.dingbang.myworld.common.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/**
 * 将对象转换为字符串后进行反序列化。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class WriteAsString extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return JsonUtils.mapper().writeValueAsString(p.readValueAs(Object.class));
    }
}
