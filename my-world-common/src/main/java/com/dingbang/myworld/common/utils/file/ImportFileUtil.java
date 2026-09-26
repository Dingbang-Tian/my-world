package com.dingbang.myworld.common.utils.file;

import com.dingbang.myworld.common.exception.BusinessException;
import com.dingbang.myworld.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;

/**
 * 导入文件读取工具类。
 *
 * @author Sebastian
 * @since 2026/09/25
 */
@Slf4j
public class ImportFileUtil {

    public static InputStream getInputStreamFromUrl(String urlStr) {
        InputStream inputStream;
        try {
            //url解码
            URL url = new URL(URLDecoder.decode(urlStr, "UTF-8"));
            inputStream = url.openStream();
        } catch (IOException e) {
            log.warn("get inputstream from {} failed, msg={}", urlStr, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FILE.FORMAT.ERROR");
        }
        return inputStream;
    }

    /**
     * 从url获取输入流，不进行url解码
     * @param urlStr
     * @return
     */
    public static InputStream getInputStreamFromUrlNoDecode(String urlStr) {
        InputStream inputStream;
        try {
            //url解码
            URL url = new URL(urlStr);
            inputStream = url.openStream();
        } catch (IOException e) {
            log.warn("get inputstream from {} failed, msg={}", urlStr, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FILE.FORMAT.ERROR");
        }
        return inputStream;
    }
}
