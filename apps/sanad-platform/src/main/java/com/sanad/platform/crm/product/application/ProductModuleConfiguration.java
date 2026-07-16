package com.sanad.platform.crm.product.application;

import com.sanad.platform.crm.product.domain.ProductRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductModuleConfiguration {
    @Bean
    public ProductUseCases productUseCases(ProductRepository repo) { return new ProductUseCases(repo); }
}
