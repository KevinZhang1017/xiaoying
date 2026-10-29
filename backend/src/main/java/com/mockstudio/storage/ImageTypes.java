package com.mockstudio.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 通过文件头（magic bytes）识别真实图片类型，而不是信任客户端声明的 Content-Type。 */
public final class ImageTypes {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";

    private static final byte[] PNG_SIG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageTypes() {
    }

    public static String detect(byte[] header, int length) {
        if (length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (length >= PNG_SIG.length) {
            boolean png = true;
            for (int i = 0; i < PNG_SIG.length; i++) {
                if (header[i] != PNG_SIG[i]) {
                    png = false;
                    break;
                }
            }
            if (png) {
                return PNG;
            }
        }
        return null;
    }

    public static String detect(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16];
            int n = in.readNBytes(buf, 0, buf.length);
            return detect(buf, n);
        }
    }

    public static String extension(String mime) {
        return PNG.equals(mime) ? "png" : "jpg";
    }

    public static String label(String mime) {
        return switch (mime) {
            case JPEG -> "JPG";
            case PNG -> "PNG";
            default -> mime;
        };
    }
}
