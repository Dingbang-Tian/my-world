package com.dingbang.myworld.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/**
 * 将字符串恢复为对象后进行序列化。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
public class ReadValueAsObject extends JsonSerializer<String> {
    @Override
    public void serialize(String s, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        try {
            jsonGenerator.writeObject(JsonUtils.mapper());
        } catch (IOException e) {
            throw e;
        }
    }
}
