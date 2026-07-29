package com.dbtraining.reconx;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Test-only Spring Boot configuration for the `repository` module.
 *
 * <p>{@code @DataJpaTest} walks up the package tree looking for a
 * {@code @SpringBootConfiguration}. The real one ({@code ReconxApplication}) lives in
 * `api`, which this module cannot depend on — the reactor edges only point upward —
 * so the module needs its own. Sitting in {@code com.dbtraining.reconx} means the
 * entity scan picks up {@code domain} and the repository scan picks up
 * {@code repository}.
 *
 * <p>{@code @EnableJpaAuditing} is here for the same reason: `api` owns
 * {@code JpaConfig}, so without this the {@code @CreatedDate} /
 * {@code @LastModifiedDate} fields stay null in these tests.
 */
@SpringBootApplication
@EnableJpaAuditing
public class RepositoryTestApplication {
}
