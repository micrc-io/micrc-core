package io.micrc.lib;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
}
