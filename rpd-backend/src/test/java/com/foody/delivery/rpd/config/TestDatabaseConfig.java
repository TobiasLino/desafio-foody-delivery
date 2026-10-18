package com.foody.delivery.rpd.config;

import com.foody.delivery.rpd.infra.config.DatabaseCommonsConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@TestConfiguration
@Import({ DatabaseCommonsConfig.class, JpaTestConfig.class })
public class TestDatabaseConfig {

    @Bean
    public JdbcTemplate template(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
