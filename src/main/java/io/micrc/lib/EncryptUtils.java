package io.micrc.lib;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密工具
 *
 * @author tengwang
 * @date 2022/11/9 11:58
 * @since 0.0.1
 */
@Slf4j
public class EncryptUtils {

    /**
     * 获取HMACSHA256加密结果串
     *
     * @param data
     * @param key
     * @return
     */
    public static String HMACSHA256(String data, String key) {
        String rtn = "";
        try {
            Mac sha256_HMAC = Mac.getInstance("HMACSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HMACSHA256");
            sha256_HMAC.init(secret_key);
            byte[] array = sha256_HMAC.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte item : array) {
                sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
            }
            rtn = sb.toString();
        } catch (Exception e) {
            log.error("HMACSHA256 加密异常：{}", e);
        }
        return rtn;

    }
}
