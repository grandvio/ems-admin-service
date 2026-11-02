package com.ems.admin.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.config.EmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;

@Slf4j
@Service
public class AliyunObjectStorage implements ObjectStorage, DisposableBean {
    private final String bucket;
    private final OSS api;
    private final OSS signer;

    public AliyunObjectStorage(EmsProperties properties) {
        EmsProperties.Oss oss = properties.getOss();
        if (!StringUtils.hasText(oss.getBucket()) || !StringUtils.hasText(oss.getAccessKeyId())
                || !StringUtils.hasText(oss.getAccessKeySecret())) {
            throw new IllegalStateException("未配置 OSS Bucket 或访问密钥");
        }
        this.bucket = oss.getBucket().trim();
        this.api = build(oss.getEndpoint(), oss, false);
        this.signer = build(oss.getPublicEndpoint(), oss, true);
    }

    @Override
    public String presignPut(String objectKey, String contentType, Duration ttl) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.PUT);
        request.setExpiration(expiration(ttl));
        request.setContentType(contentType);
        return sign(request);
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.GET);
        request.setExpiration(expiration(ttl));
        return sign(request);
    }

    @Override
    public StoredObject head(String objectKey) {
        try {
            ObjectMetadata metadata = api.getObjectMetadata(bucket, objectKey);
            return new StoredObject(metadata.getContentLength(), metadata.getContentType());
        } catch (OSSException ex) {
            if ("NoSuchKey".equals(ex.getErrorCode())) {
                return null;
            }
            log.warn("读取对象信息失败 key={} code={}", objectKey, ex.getErrorCode());
            throw BizException.unavailable("对象存储暂不可用");
        } catch (ClientException ex) {
            log.warn("读取对象信息失败 key={} msg={}", objectKey, ex.getMessage());
            throw BizException.unavailable("对象存储暂不可用");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            api.deleteObject(bucket, objectKey);
        } catch (OSSException ex) {
            if ("NoSuchKey".equals(ex.getErrorCode())) {
                return;
            }
            log.warn("删除对象失败 key={} code={}", objectKey, ex.getErrorCode());
            throw BizException.unavailable("对象存储暂不可用");
        } catch (ClientException ex) {
            log.warn("删除对象失败 key={} msg={}", objectKey, ex.getMessage());
            throw BizException.unavailable("对象存储暂不可用");
        }
    }

    @Override
    public void destroy() {
        shutdown(api);
        shutdown(signer);
    }

    private String sign(GeneratePresignedUrlRequest request) {
        try {
            return signer.generatePresignedUrl(request).toString();
        } catch (RuntimeException ex) {
            log.warn("签发访问地址失败 key={}", request.getKey(), ex);
            throw BizException.unavailable("对象存储暂不可用");
        }
    }

    private static Date expiration(Duration ttl) {
        long millis = Math.max(ttl.toMillis(), 1000);
        return new Date(System.currentTimeMillis() + millis);
    }

    private static OSS build(String endpoint, EmsProperties.Oss oss, boolean cname) {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSupportCname(cname);
        configuration.setConnectionTimeout(3000);
        configuration.setSocketTimeout(5000);
        configuration.setRequestTimeout(8000);
        return new OSSClientBuilder().build(normalize(endpoint), oss.getAccessKeyId().trim(), oss.getAccessKeySecret().trim(), configuration);
    }

    private static String normalize(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("未配置 OSS Endpoint");
        }
        String value = endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return value;
    }

    private static void shutdown(OSS client) {
        if (client != null) {
            client.shutdown();
        }
    }
}
