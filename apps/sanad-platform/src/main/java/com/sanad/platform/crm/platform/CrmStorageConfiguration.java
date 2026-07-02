package com.sanad.platform.crm.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "sanad.crm.platform.storage", name = "enabled", havingValue = "true")
public class CrmStorageConfiguration {

    @Bean
    S3Client crmS3Client(CrmPlatformProperties properties) {
        var storage = properties.getStorage();
        var builder = S3Client.builder()
            .region(Region.of(storage.getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(storage.isPathStyleAccess())
                .build());
        if (storage.getEndpoint() != null) {
            builder.endpointOverride(storage.getEndpoint());
        }
        return builder.build();
    }

    @Bean
    S3Presigner crmS3Presigner(CrmPlatformProperties properties) {
        var storage = properties.getStorage();
        var builder = S3Presigner.builder()
            .region(Region.of(storage.getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(storage.isPathStyleAccess())
                .build());
        if (storage.getEndpoint() != null) {
            builder.endpointOverride(storage.getEndpoint());
        }
        return builder.build();
    }
}
