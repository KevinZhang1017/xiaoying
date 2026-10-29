package com.mockstudio.storage;

import com.mockstudio.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地文件存储。数据库只保存相对路径，根目录可通过 STORAGE_ROOT 指向容器数据卷。
 */
@Service
public class StorageService {

    private final Path root;

    public StorageService(AppProperties props) {
        this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("uploads"));
            Files.createDirectories(root.resolve("work"));
            Files.createDirectories(root.resolve("results"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot initialise storage root " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    /** 解析相对路径，并确保不会逃逸出存储根目录。 */
    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes storage root: " + relative);
        }
        return p;
    }

    public String relativize(Path absolute) {
        return root.relativize(absolute.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public Path userUploadDir(long userId) throws IOException {
        return Files.createDirectories(root.resolve("uploads").resolve(Long.toString(userId)));
    }

    public Path workFile(long taskId) {
        return root.resolve("work").resolve(taskId + ".jpg");
    }

    public Path resultFile(long taskId) {
        return root.resolve("results").resolve(taskId + ".jpg");
    }
}
