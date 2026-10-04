package com.dingbang.myworld.common.utils;


/**
 * 地理坐标计算工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
public class GeoUtils {

    /**
     * 计算距离
     *
     * @param latitude1 第一个坐标点纬度
     * @param longitude1 第一个坐标点经度
     * @param latitude2 第二个坐标点纬度
     * @param longitude2 第二个坐标点经度
     * @return 距离
     */
    public static double distance(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadiusKm = 6371.0088D;
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * 判断点是否在围栏中
     *
     * @param point 点
     * @param points 围栏多边形
     * @return 是否在围栏中
     */
    public static boolean isInPolygon(GeoPoint point, GeoPoint[] points) {
        int nCross = 0;
        for (int i = 0; i < points.length; i++) {
            GeoPoint p1 = points[i];
            GeoPoint p2 = points[(i + 1) % points.length];
            if(p1 == null || p2 == null){
                continue;
            }
            // 求解 y=p.latitude 与 p1 p2 的交点
            // p1p2 与 y=p0.latitude平行
            if (p1.getLat() == p2.getLat()) {
                continue;
            }
            // 交点在p1p2延长线上
            if (point.getLat() < Math.min(p1.getLat(), p2.getLat())) {
                continue;
            }
            // 交点在p1p2延长线上
            if (point.getLat() >= Math.max(p1.getLat(), p2.getLat())) {
                continue;
            }
            // 求交点的 X 坐标
            double x = (point.getLat() - p1.getLat())
                    * (p2.getLon() - p1.getLon()) / (p2.getLat() - p1.getLat())
                    + p1.getLon();
            // 只统计单边交点
            if (x > point.getLon()) {
                nCross++;
            }
        }
        return (nCross % 2 != 0);
    }

    /**
     * 解析围栏
     */
    public static GeoPoint[] parseRange(String range) {
        // 解析围栏信息
        String[] pieces = range.split(";");
        GeoPoint[] points = new GeoPoint[pieces.length];
        for (int i = 0, count = pieces.length; i < count; i++) {
            String[] location = pieces[i].split(",");
            if (location.length != 2) {
                return null;
            } else {
                double lon = Double.parseDouble(location[0]);
                double lat = Double.parseDouble(location[1]);
                points[i] = new GeoPoint(lat, lon);
            }
        }
        return points;
    }

}
