package com.dingbang.myworld.common.utils.time;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 时间区间数据对象。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeInterval {
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
}
