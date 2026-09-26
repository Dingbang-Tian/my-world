package com.dingbang.myworld.common.utils.excel;

import com.alibaba.excel.EasyExcel;
import com.dingbang.myworld.common.utils.file.ImportFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Excel 数据处理工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class ExcelUtil {

    /**
     * 读取 Excel
     *
     * @param url      文件
     * @param modelClass 实体类映射，继承 BaseRowModel 类
     * @return Excel 数据 list
     */
    public static <T> List<T> readExcel(String url, Class<T> modelClass) {
        return EasyExcel.read(ImportFileUtil.getInputStreamFromUrl(url)).head(modelClass).sheet().headRowNumber(1).doReadSync();
    }

    /**
     * 读取 Excel(指定sheet)
     *
     * @param url      文件
     * @param modelClass 实体类映射，继承 BaseRowModel 类
     * @return Excel 数据 list
     */
    public static <T> List<T> readExcelSingleSheet(String url, Class<T> modelClass, int sheetNo) {
        return EasyExcel.read(ImportFileUtil.getInputStreamFromUrl(url)).head(modelClass).sheet(sheetNo).headRowNumber(1).doReadSync();
    }
}
