package com.dingbang.myworld.common.utils.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.dingbang.myworld.common.utils.ZonedDateTimeUtils;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 本地日期时间 UTC 序列化器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class UtcLocalDateTimeJsonSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String utcDateStr = ZonedDateTimeUtils.convertLocalDateTimeToString(value);
        gen.writeString(utcDateStr);
    }
}
