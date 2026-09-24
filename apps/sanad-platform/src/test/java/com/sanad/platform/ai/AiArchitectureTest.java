package com.sanad.platform.ai;

import com.sanad.platform.ai.api.AiController;
import com.sanad.platform.ai.application.AiAgentService;
import com.sanad.platform.ai.application.AiExecutionService;
import com.sanad.platform.ai.domain.*;
import com.sanad.platform.ai.infrastructure.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Architecture verification for the AI Module. */
class AiArchitectureTest {

    @Nested
    class ControllerLayer {
        @Test
        void controllerIsInApiPackage() {
            assertThat(AiController.class.getPackageName()).isEqualTo("com.sanad.platform.ai.api");
        }

        @Test
        void controllerHasNoJdbcFields() {
            for (Field f : AiController.class.getDeclaredFields()) {
                assertThat(f.getType().getName()).doesNotContain("JdbcTemplate");
                assertThat(f.getType().getName()).doesNotContain("DataSource");
            }
        }

        @Test
        void controllerFieldsAreApplicationServices() {
            for (Field f : AiController.class.getDeclaredFields()) {
                assertThat(f.getType().getName()).contains(".application.");
            }
        }

        @Test
        void controllerDoesNotImportRepositories() {
            for (Field f : AiController.class.getDeclaredFields()) {
                assertThat(f.getType().getName())
                        .as("controller must not depend on repository: " + f.getName())
                        .doesNotContain(".domain.").doesNotContain(".infrastructure.");
            }
        }
    }

    @Nested
    class ServiceLayer {
        @Test
        void allServicesInApplicationPackage() {
            assertThat(AiAgentService.class.getPackageName()).isEqualTo("com.sanad.platform.ai.application");
            assertThat(AiExecutionService.class.getPackageName()).isEqualTo("com.sanad.platform.ai.application");
        }

        @Test
        void servicesHaveServiceAnnotation() {
            assertThat(AiAgentService.class.isAnnotationPresent(
                    org.springframework.stereotype.Service.class)).isTrue();
            assertThat(AiExecutionService.class.isAnnotationPresent(
                    org.springframework.stereotype.Service.class)).isTrue();
        }

        @Test
        void writeMethodsAreTransactional() {
            for (Method m : AiAgentService.class.getDeclaredMethods()) {
                var name = m.getName();
                if (List.of("create", "activate", "deactivate", "archive", "delete").contains(name)) {
                    assertThat(m.isAnnotationPresent(
                            org.springframework.transaction.annotation.Transactional.class))
                            .as("AiAgentService." + name + " must be @Transactional").isTrue();
                }
            }
            // AiExecutionService.execute is @Transactional
            var execMethod = Arrays.stream(AiExecutionService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("execute")).findFirst().orElseThrow();
            assertThat(execMethod.isAnnotationPresent(
                    org.springframework.transaction.annotation.Transactional.class)).isTrue();
        }
    }

    @Nested
    class DomainLayer {
        @Test
        void domainRecordsArePure() {
            for (Class<?> c : List.of(AiAgent.class, AiInference.class)) {
                assertThat(c.isRecord()).isTrue();
                assertThat(c.getPackageName()).isEqualTo("com.sanad.platform.ai.domain");
                assertThat(c.getAnnotations()).isEmpty();
            }
        }

        @Test
        void aiAgentHasStateTransitions() {
            assertThat(Arrays.stream(AiAgent.class.getDeclaredMethods())
                    .map(Method::getName).toList())
                    .contains("activate", "deactivate", "archive");
        }

        @Test
        void aiInferenceIsImmutable() {
            // AiInference has complete() and fail() — no setState() mutators
            var methods = Arrays.stream(AiInference.class.getDeclaredMethods())
                    .map(Method::getName).toList();
            assertThat(methods).contains("start", "complete", "fail");
            assertThat(methods).doesNotContain("setState", "setStatus", "updateOutput");
        }

        @Test
        void advisoryOnlyConstantIsTrue() {
            assertThat(AiInference.ADVISORY_ONLY).isTrue();
        }
    }

    @Nested
    class InfrastructureLayer {
        @Test
        void jdbcReposInInfrastructurePackage() {
            assertThat(JdbcAiAgentRepository.class.getPackageName())
                    .isEqualTo("com.sanad.platform.ai.infrastructure");
            assertThat(JdbcAiInferenceRepository.class.getPackageName())
                    .isEqualTo("com.sanad.platform.ai.infrastructure");
        }

        @Test
        void jdbcReposImplementInterfaces() {
            assertThat(JdbcAiAgentRepository.class.getInterfaces()).contains(AiAgentRepository.class);
            assertThat(JdbcAiInferenceRepository.class.getInterfaces()).contains(AiInferenceRepository.class);
        }

        @Test
        void jdbcReposAreAnnotated() {
            assertThat(JdbcAiAgentRepository.class.isAnnotationPresent(
                    org.springframework.stereotype.Repository.class)).isTrue();
            assertThat(JdbcAiInferenceRepository.class.isAnnotationPresent(
                    org.springframework.stereotype.Repository.class)).isTrue();
        }
    }
}
