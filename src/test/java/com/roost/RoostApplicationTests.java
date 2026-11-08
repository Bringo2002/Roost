package com.roost;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the whole application against a real, empty PostgreSQL so Flyway
 * applies every migration at startup, same as production. It used to rely on
 * a database being available at localhost, which stops working once startup
 * runs migrations.
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@Testcontainers
class RoostApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

	@Test
	void contextLoads() {
	}

}
