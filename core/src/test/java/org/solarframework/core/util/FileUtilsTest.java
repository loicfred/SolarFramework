package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUtilsTest {

    @Test
    void getFileSizeFormatsBytes() {
        assertEquals("500 B", FileUtils.getFileSize(500L));
    }
    @Test
    void getFileSizeFormatsKilobytes() {
        assertEquals("2.0 KB", FileUtils.getFileSize(2048L));
    }
    @Test
    void getFileSizeFormatsMegabytes() {
        assertEquals("5.0 MB", FileUtils.getFileSize(5L * 1024 * 1024));
    }
    @Test
    void getFileSizeFormatsGigabytes() {
        assertEquals("2.0 GB", FileUtils.getFileSize(2L * 1024 * 1024 * 1024));
    }
    @Test
    void getFileSizeFromByteArrayUsesArrayLength() {
        assertEquals("3 B", FileUtils.getFileSize(new byte[3]));
    }
}
