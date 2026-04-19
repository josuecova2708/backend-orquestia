package com.orquestia.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final MinioService minioService;

    @GetMapping("/presign")
    public Map<String, String> getPresignedUrl(
        @RequestParam String filename,
        @RequestParam(defaultValue = "application/octet-stream") String contentType
    ) {
        MinioService.PresignResult result = minioService.generarPresignedUrl(filename, contentType);
        return Map.of(
            "uploadUrl", result.uploadUrl(),
            "publicUrl", result.publicUrl(),
            "key", result.key()
        );
    }
}
