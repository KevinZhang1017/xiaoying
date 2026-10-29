package com.mockstudio.asset;

import com.mockstudio.auth.AuthUser;
import com.mockstudio.common.ApiException;
import com.mockstudio.common.Times;
import com.mockstudio.storage.ImageTypes;
import com.mockstudio.storage.StorageService;
import com.mockstudio.template.Template;
import com.mockstudio.template.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssetService {

    /** 不指定模板时的全局上限，与 multipart 配置保持一致。 */
    static final long GLOBAL_MAX_BYTES = 10L * 1024 * 1024;
    static final List<String> GLOBAL_ACCEPTED = List.of(ImageTypes.JPEG, ImageTypes.PNG);

    private final AssetMapper mapper;
    private final StorageService storage;
    private final TemplateService templateService;
    private final Clock clock;

    public AssetService(AssetMapper mapper, StorageService storage, TemplateService templateService, Clock clock) {
        this.mapper = mapper;
        this.storage = storage;
        this.templateService = templateService;
        this.clock = clock;
    }

    public Asset upload(AuthUser user, MultipartFile file, String templateId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "请选择要上传的图片");
        }
        Template template = templateId == null || templateId.isBlank() ? null : templateService.require(templateId);
        long maxBytes = template == null ? GLOBAL_MAX_BYTES : template.getMaxFileSizeBytes();
        List<String> accepted = template == null ? GLOBAL_ACCEPTED : template.acceptedMimeTypeList();

        if (file.getSize() > maxBytes) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "图片大小不能超过 " + formatSize(maxBytes),
                    Map.of("maxFileSizeBytes", maxBytes, "actualBytes", file.getSize()));
        }

        try (InputStream raw = file.getInputStream();
             BufferedInputStream in = new BufferedInputStream(raw)) {
            in.mark(16);
            byte[] header = new byte[16];
            int n = in.readNBytes(header, 0, header.length);
            in.reset();
            String mime = ImageTypes.detect(header, n);
            if (mime == null || !accepted.contains(mime)) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "UNSUPPORTED_MIME",
                        "仅支持 " + accepted.stream().map(ImageTypes::label).collect(Collectors.joining(" / ")) + " 格式",
                        Map.of("acceptedMimeTypes", accepted));
            }

            Path dir = storage.userUploadDir(user.id());
            Path target = dir.resolve(UUID.randomUUID() + "." + ImageTypes.extension(mime));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long written;
            try (DigestInputStream din = new DigestInputStream(in, md)) {
                written = Files.copy(din, target);
            }

            Asset asset = new Asset();
            asset.setUserId(user.id());
            asset.setStoragePath(storage.relativize(target));
            asset.setOriginalFilename(sanitize(file.getOriginalFilename()));
            asset.setMimeType(mime);
            asset.setSizeBytes(written);
            asset.setSha256(HexFormat.of().formatHex(md.digest()));
            asset.setCreatedAt(Times.now(clock));
            mapper.insert(asset);
            return asset;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Asset requireOwned(AuthUser user, long id) {
        Asset asset = mapper.findByIdForUser(id, user.id());
        if (asset == null) {
            throw ApiException.notFound("ASSET_NOT_FOUND", "素材不存在");
        }
        return asset;
    }

    public Path fileOf(Asset asset) {
        return storage.resolve(asset.getStoragePath());
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    public static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            double mb = bytes / 1024.0 / 1024.0;
            return (mb == Math.floor(mb) ? String.valueOf((long) mb) : String.format("%.1f", mb)) + "MB";
        }
        return (bytes / 1024) + "KB";
    }
}
