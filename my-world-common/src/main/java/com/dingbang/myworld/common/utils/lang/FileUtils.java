package com.dingbang.myworld.common.utils.lang;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.dingbang.myworld.common.exception.BusinessException;
import com.dingbang.myworld.common.exception.ErrorCode;
import com.dingbang.myworld.common.utils.number.FitNums;
import com.dingbang.myworld.common.utils.number.Numbers;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.util.function.Consumer;

/**
 * 文件操作工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class FileUtils extends FileUtil {

    /**
     * 旧版 Excel 文件扩展名。
     */
    public static final String XLS_EXT = "xls";
    /**
     * 新版 Excel 文件扩展名。
     */
    public static final String XLSX_EXT = "xlsx";
    /**
     * 允许公开读取的目录。
     */
    public static final String PUBLIC_READ_DIR = "public";
    /**
     * 允许私有读取的目录。
     */
    public static final String PRIVATE_READ_DIR = "private";
    /**
     * 默认允许处理的文件最大字节数。
     */
    public static final long DEFAULT_MAX_SIZE = 4 * 1024 * 1024;

    /**
     * 校验是否限定的Excel类型文件
     */
    public static void checkQualifiedExcelFile(String urlOrFileName) {
        if (StringUtils.isBlank(urlOrFileName)) {
            return;
        }
        boolean isExcelFile = StringUtils.equalsAnyIgnoreCase(FilenameUtils.getExtension(urlOrFileName), XLS_EXT, XLSX_EXT);
        if (!isExcelFile) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "not a excel file");
        }
    }

    /**
     * 构建对应的错误文件名
     */
    public static String buildErrorFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return StringUtils.EMPTY;
        }

        int indexOfExt = FilenameUtils.indexOfExtension(fileName);
        return org.apache.commons.lang3.StringUtils.overlay(fileName, "error", indexOfExt, indexOfExt);
    }

    /**
     * 自动生成随机文件名
     */
    public static String randomFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return StringUtils.EMPTY;
        }

        return String.format("%s.%s", IdUtil.fastSimpleUUID(), FilenameUtils.getExtension(fileName));
    }

    /**
     * 自动生成公有读文件路径
     *    默认：自动生成随机文件名
     *
     * @param fileName 原始文件名
     */
    public static String buildPublicFilePath(String fileName) {
        return buildPublicFilePath(fileName, true);
    }

    /**
     * 自动生成公有读文件路径
     *
     * @param fileName 原始文件名
     * @param needRandomFileName 是否需要生成随机文件名
     */
    public static String buildPublicFilePath(String fileName, boolean needRandomFileName) {
        return buildFilePath(fileName, true, needRandomFileName);
    }

    /**
     * 自动生成文件路径
     *    默认：自动生成随机文件名
     *
     * @param fileName 原始文件名
     * @param isPublic 是否公有读
     */
    public static String buildFilePath(String fileName, boolean isPublic) {
        return buildFilePath(fileName, isPublic, true);
    }

    /**
     * 自动生成文件路径
     *
     * @param fileName 原始文件名
     * @param isPublic 是否公有读
     * @param needRandomFileName 是否需要生成随机文件名
     */
    public static String buildFilePath(String fileName, boolean isPublic, boolean needRandomFileName) {
        return String.format("%s/%s", isPublic ? PUBLIC_READ_DIR : PRIVATE_READ_DIR, needRandomFileName ? randomFileName(fileName) : fileName);
    }

    /**
     * 根据文件链接读取文件流后执行操作
     *    默认：4MB
     */
    public static void doWithFileInputStream(String fileUrl, Consumer<InputStream> inputStreamConsumer) {
        doWithFileInputStream(fileUrl, inputStreamConsumer, DEFAULT_MAX_SIZE);
    }

    /**
     * 根据文件链接读取文件流后执行操作
     */
    public static void doWithFileInputStream(@NonNull String fileUrl, @NonNull Consumer<InputStream> inputStreamConsumer, long maxSize) {
        try {
            byte[] bytes = IOUtils.toByteArray(new URL(fileUrl));
            // 文件大小校验
            if (bytes.length > maxSize) {
                BigDecimal size = Numbers.halfUpDivide(Numbers.of(maxSize), Numbers.of(FitNums.INT_1M), FitNums.INT_2);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "max file size is:" + size + "MB");
            }
            inputStreamConsumer.accept(new ByteArrayInputStream(bytes));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【URL读取文件失败】fileUrl={}", fileUrl, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Failed to read from file url: " + fileUrl);
        }
    }

    /**
     * 校验Excel类型文件并根据文件链接读取文件流后执行操作
     *    默认：4MB
     */
    public static void validExcelAndDoWithFileInputStream(String fileUrl, Consumer<InputStream> inputStreamConsumer) {
        validExcelAndDoWithFileInputStream(fileUrl, inputStreamConsumer, DEFAULT_MAX_SIZE);
    }

    /**
     * 校验Excel类型文件并根据文件链接读取文件流后执行操作
     */
    public static void validExcelAndDoWithFileInputStream(String fileUrl, Consumer<InputStream> inputStreamConsumer, long maxSize) {
        checkQualifiedExcelFile(fileUrl);
        doWithFileInputStream(fileUrl, inputStreamConsumer, maxSize);
    }

}
