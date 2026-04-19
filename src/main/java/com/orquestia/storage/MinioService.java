package com.orquestia.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucket;
    private final String endpoint;

    public MinioService(
        @Value("${minio.endpoint}") String endpoint,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey,
        @Value("${minio.bucket}") String bucket
    ) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }

    public record PresignResult(String uploadUrl, String publicUrl, String key) {}

    public PresignResult generarPresignedUrl(String filename, String contentType) {
        try {
            String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String key = "tareas/" + UUID.randomUUID() + "-" + safeFilename;

            String uploadUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(key)
                    .expiry(5, TimeUnit.MINUTES)
                    .build()
            );

            String publicUrl = endpoint + "/" + bucket + "/" + key;

            return new PresignResult(uploadUrl, publicUrl, key);
        } catch (Exception e) {
            throw new RuntimeException("Error generando URL presignada: " + e.getMessage(), e);
        }
    }
}
