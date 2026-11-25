package com.dippie.ai;

import com.dippie.catalog.importing.CatalogImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(AiIndexConfiguration.HasBaseUrl.class)
class AiIndexConfiguration {

    @Bean
    HttpCatalogIndexPublisher httpCatalogIndexPublisher(AiServiceClient client, ObjectMapper mapper) {
        return new HttpCatalogIndexPublisher(client, mapper);
    }

    @Bean
    CatalogIndexBootstrapper catalogIndexBootstrapper(AiServiceClient client, CatalogImportService catalog) {
        return new CatalogIndexBootstrapper(client, catalog);
    }

    static class HasBaseUrl implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("dippie.ai.base-url"));
        }
    }
}
