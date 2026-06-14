package com.muse.muaiagent.constant;

/**
 * 文件常量
 */
public interface FileConstant {

    String FILE_SAVE_DIR = System.getProperty("file.save.dir", System.getProperty("user.dir") + "/tmp");
}
