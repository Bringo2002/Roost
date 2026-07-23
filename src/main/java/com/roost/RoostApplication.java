package com.roost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RoostApplication {

	public static void main(String[] args) {
		fixJdbcUrl();
		SpringApplication.run(RoostApplication.class, args);
	}

	private static void fixJdbcUrl() {
		String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
		if (dbUrl == null || dbUrl.isEmpty()) {
			dbUrl = System.getenv("DATABASE_URL");
		}
		if (dbUrl != null && !dbUrl.isEmpty()) {
			if (dbUrl.startsWith("postgres://")) {
				dbUrl = "jdbc:postgresql://" + dbUrl.substring("postgres://".length());
				System.setProperty("spring.datasource.url", dbUrl);
			} else if (dbUrl.startsWith("postgresql://")) {
				dbUrl = "jdbc:postgresql://" + dbUrl.substring("postgresql://".length());
				System.setProperty("spring.datasource.url", dbUrl);
			}
		}
	}
}

