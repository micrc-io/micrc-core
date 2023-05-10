package io.micrc.lib;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

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

    /**
     * 通过pbkdf2加密数据
     *
     * @param data
     * @param salt
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    public static String pbkdf2(String data, String salt) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(data.toCharArray(), fromHex(salt), 1000, 512);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return toHex(f.generateSecret(spec).getEncoded());
    }

    /**
     * 通过加密的强随机数生成盐(最后转换为16进制)
     *
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String generateSalt() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return toHex(salt);
    }

    private static byte[] fromHex(String hex) {
        byte[] binary = new byte[hex.length() / 2];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return binary;
    }

    private static String toHex(byte[] array) {
        BigInteger bi = new BigInteger(1, array);
        String hex = bi.toString(16);
        int paddingLength = (array.length * 2) - hex.length();
        if (paddingLength > 0) {
            return String.format("%0" + paddingLength + "d", 0) + hex;
        } else {
            return hex;
        }
    }
}
