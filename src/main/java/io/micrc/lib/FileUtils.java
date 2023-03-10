package io.micrc.lib;

import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * TODO
 *
 * @author tengwang
 * @date 2022/11/18 19:54
 * @since 0.0.1
 */
public class FileUtils {

    public static File writeTempFile(String prefix, String suffix, String content) throws IOException {
        File file = File.createTempFile(prefix, "." + suffix);
        file.deleteOnExit();
        BufferedWriter out = new BufferedWriter(new FileWriter(file));
        out.write(content);
        out.close();
        return file;
    }

    public static void deleteFile(File file){
        file.delete();
    }

    public static String fileReader(String filePath, List<String> suffixList) {
        if (!StringUtils.hasText(filePath) || null == suffixList || suffixList.stream().noneMatch(filePath::endsWith)) {
            throw new RuntimeException("the file suffix invalid.");
        }
        return fileReader(filePath);
    }

    public static String fileReader(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new RuntimeException("the file path invalid.");
        }
        StringBuffer fileContent = new StringBuffer();
        try {
            InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(filePath);
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String str = null;
            while ((str = in.readLine()) != null) {
                fileContent.append(str).append("\n");
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(filePath + " file not found or can`t resolve...");
        }
        return fileContent.toString();
    }
}
