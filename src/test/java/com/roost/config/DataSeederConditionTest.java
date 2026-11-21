package com.roost.config;

import com.roost.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Guards when {@link DataSeeder} exists. It inserts 50 fake Nairobi
 * listings whenever the table has fewer than 10 rows, so it must never be
 * created unless seeding is explicitly switched on. It used to be tied to
 * {@code @Profile("!production")}, which left it active on the live
 * {@code aws} profile.
 */
class DataSeederConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DataSeeder.class)
            .withBean(PropertyRepository.class, () -> mock(PropertyRepository.class));

    @Test
    void seeder_isNotCreatedByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    void seeder_isNotCreatedOnTheAwsProfile() {
        runner.withPropertyValues("spring.profiles.active=aws")
                .run(context -> assertThat(context).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    void seeder_isNotCreatedOnTheProductionProfile() {
        runner.withPropertyValues("spring.profiles.active=production")
                .run(context -> assertThat(context).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    void seeder_isNotCreatedWhenSeedingIsExplicitlyDisabled() {
        runner.withPropertyValues("app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    void seeder_isCreatedOnlyWhenSeedingIsExplicitlyEnabled() {
        runner.withPropertyValues("app.seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DataSeeder.class));
    }
}
