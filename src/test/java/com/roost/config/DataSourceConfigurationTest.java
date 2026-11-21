package com.roost.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the datasource wiring: the application must use Spring Boot's own
 * datasource configuration, so the connection comes from the standard
 * {@code spring.datasource.*} settings (and Testcontainers' service
 * connection in tests) and the {@code spring.datasource.hikari.*} pool
 * settings are actually applied. A hand-built {@code DataSource} bean
 * silently bypasses both.
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@Testcontainers
class DataSourceConfigurationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

	@Autowired
	private DataSource dataSource;

	@Test
	void dataSourceConnectsToTheConfiguredDatabase() throws Exception {
		HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

		assertEquals(postgres.getJdbcUrl(), hikari.getJdbcUrl());
	}

	@Test
	void hikariPropertiesAreApplied() throws Exception {
		HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

		// spring.datasource.hikari.initialization-fail-timeout=-1 in application.properties
		assertEquals(-1, hikari.getInitializationFailTimeout());
	}

}
