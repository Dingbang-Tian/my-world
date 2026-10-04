package com.dingbang.myworld.common.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地理坐标点。
 *
 * @author Sebastian
 * @since 2026/10/04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    /**
     * 纬度
     */
    private double lat;

    /**
     * 纬度
     */
    private double lon;
}
