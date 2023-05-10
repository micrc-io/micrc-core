package io.micrc.lib;

public class StringUtil {

    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    public static String lowerStringFirst(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        char[] strChars = str.toCharArray();
        strChars[0] += 32;
        return String.valueOf(strChars);
    }

    public static String upperStringFirst(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        char[] strChars = str.toCharArray();
        strChars[0] -= 32;
        return String.valueOf(strChars);
    }
}
