package com.orquestia.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
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

    // Usado por tareas de formulario (ruta genérica)
    public PresignResult generarPresignedUrl(String filename, String contentType) {
        try {
            String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String key = "tareas/" + UUID.randomUUID() + "-" + safeFilename;
            return buildPresign(key);
        } catch (Exception e) {
            throw new RuntimeException("Error generando URL presignada: " + e.getMessage(), e);
        }
    }

    // Usado por el SGD — ruta organizada: documentos/{empresaId}/{instanciaId?}/{uuid}-{filename}
    public PresignResult generarPresignedUrlSGD(String empresaId, String instanciaId, String filename) {
        try {
            String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String folder = instanciaId != null
                ? "documentos/" + empresaId + "/" + instanciaId
                : "documentos/" + empresaId;
            String key = folder + "/" + UUID.randomUUID() + "-" + safeFilename;
            return buildPresign(key);
        } catch (Exception e) {
            throw new RuntimeException("Error generando URL presignada SGD: " + e.getMessage(), e);
        }
    }

    // URL de descarga temporal (GET presignado — 1 hora)
    public String generarUrlDescarga(String key) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(1, TimeUnit.HOURS)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error generando URL de descarga: " + e.getMessage(), e);
        }
    }

    // Re-sube un archivo desde InputStream (usado para guardar versión editada por OnlyOffice)
    public void putObjectFromStream(String key, java.io.InputStream stream, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(stream, -1, 10 * 1024 * 1024) // -1 = tamaño desconocido, 10MB part size
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error subiendo objeto a MinIO: " + e.getMessage(), e);
        }
    }

    // Stream directo del objeto — para proxy hacia OnlyOffice
    public java.io.InputStream getObjectStream(String key) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo stream de MinIO: " + e.getMessage(), e);
        }
    }

    public void eliminar(String key) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object(key).build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error eliminando objeto de MinIO: " + e.getMessage(), e);
        }
    }

    private PresignResult buildPresign(String key) throws Exception {
        String uploadUrl = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(key)
                .expiry(10, TimeUnit.MINUTES)
                .build()
        );
        String publicUrl = endpoint + "/" + bucket + "/" + key;
        return new PresignResult(uploadUrl, publicUrl, key);
    }
}
