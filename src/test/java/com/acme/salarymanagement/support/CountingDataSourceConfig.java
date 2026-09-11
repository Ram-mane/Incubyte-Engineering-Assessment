package com.acme.salarymanagement.support;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Wraps the application's pool so any test can ask how many statements a piece of work issued.
 * Flyway's pool is left alone: migrations are not what an N+1 test is measuring.
 */
@TestConfiguration
public class CountingDataSourceConfig {

    @Bean
    static BeanPostProcessor countStatements() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                boolean applicationPool = bean instanceof DataSource && "dataSource".equals(beanName);
                return applicationPool ? new CountingDataSource((DataSource) bean) : bean;
            }
        };
    }
}
