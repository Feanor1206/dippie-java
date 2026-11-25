package com.dippie.catalog.importing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
class CatalogIndexPublisherConfiguration {

    @Bean
    @ConditionalOnMissingBean(CatalogIndexPublisher.class)
    NoopCatalogIndexPublisher noopCatalogIndexPublisher() {
        return new NoopCatalogIndexPublisher();
    }
}
