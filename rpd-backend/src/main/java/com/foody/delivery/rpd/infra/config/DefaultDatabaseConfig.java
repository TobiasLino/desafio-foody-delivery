package com.foody.delivery.rpd.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@Import({ DatabaseCommonsConfig.class, DefaultJpaConfig.class })
@Profile("!test")
public class DefaultDatabaseConfig {

    @Bean
    public JdbcTemplate template(final DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
