package com.boxue.api.media;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

@Service
public class S3StorageService implements StorageService {

    private final MinioClient client;
    private final MediaProperties properties;
    private volatile boolean bucketReady;

    public S3StorageService(MediaProperties properties) {
        this.properties = properties;
        this.client = MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        ensureBucket();
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .stream(input, data.length, -1)
                .contentType(contentType)
                .build());
        } catch (Exception ex) {
            throw storageError("上传媒体到对象存储失败", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        ensureBucket();
        try (InputStream input = client.getObject(GetObjectArgs.builder()
            .bucket(properties.getBucket())
            .object(key)
            .build())) {
            return input.readAllBytes();
        } catch (Exception ex) {
            throw storageError("读取媒体文件失败", ex);
        }
    }

    @Override
    public void delete(String key) {
        ensureBucket();
        try {
            client.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .build());
        } catch (Exception ex) {
            throw storageError("删除媒体文件失败", ex);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucket())
                        .build());
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw storageError("对象存储桶不可用：" + properties.getBucket(), ex);
            }
        }
    }

    private MediaException storageError(String message, Exception cause) {
        return new MediaException(HttpStatus.BAD_GATEWAY, "MEDIA_STORAGE_ERROR", message, null, cause);
    }
}
