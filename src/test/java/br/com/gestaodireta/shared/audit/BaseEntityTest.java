package br.com.gestaodireta.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    void shouldSetCreatedAtAndUpdatedAtOnCreate() {
        TestEntity entity = new TestEntity();

        entity.create();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldNotChangeCreatedAtOnUpdate() {
        TestEntity entity = new TestEntity();
        entity.create();
        LocalDateTime createdAt = entity.getCreatedAt();

        entity.update();

        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldUpdateUpdatedAtOnUpdate() {
        TestEntity entity = new TestEntity();
        entity.create();
        LocalDateTime updatedAt = entity.getUpdatedAt();

        entity.update();

        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }

    private static class TestEntity extends BaseEntity {

        void create() {
            onCreate();
        }

        void update() {
            onUpdate();
        }
    }
}
