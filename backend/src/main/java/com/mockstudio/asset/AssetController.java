package com.mockstudio.asset;

import com.mockstudio.auth.AuthUser;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    public record AssetDto(long id, String url, String mimeType, long sizeBytes, String originalFilename) {
        public static AssetDto of(Asset a) {
            return new AssetDto(a.getId(), contentUrl(a.getId()), a.getMimeType(), a.getSizeBytes(),
                    a.getOriginalFilename());
        }
    }

    public static String contentUrl(long assetId) {
        return "/api/assets/" + assetId + "/content";
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetDto> upload(AuthUser user,
                                           @RequestPart("file") MultipartFile file,
                                           @RequestParam(name = "templateId", required = false) String templateId) {
        Asset asset = service.upload(user, file, templateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetDto.of(asset));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<FileSystemResource> content(AuthUser user, @PathVariable("id") long id) {
        Asset asset = service.requireOwned(user, id);
        Path file = service.fileOf(asset);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
