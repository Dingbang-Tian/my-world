package com.dingbang.myworld.common.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.dingbang.myworld.common.utils.ZonedDateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * UTC 时间字符串反序列化器。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class UtcLocalDateTimeJsonDeserializer extends JsonDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        try {
            if(jsonParser!=null&& StringUtils.isNotEmpty(jsonParser.getText())){
                return ZonedDateTimeUtils.convertOffsetTDateTimeLocalDateTime(jsonParser.getText());
            }else {
                return null;
            }

        } catch(Exception e) {
            log.error(e.getMessage(),e);
            throw new RuntimeException(e);
        }
    }
}
