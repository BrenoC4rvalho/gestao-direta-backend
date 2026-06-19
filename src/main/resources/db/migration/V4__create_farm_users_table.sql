CREATE TABLE farm_users (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE farm_users
    ADD CONSTRAINT fk_farm_users_farm_id FOREIGN KEY (farm_id) REFERENCES farms (id);

ALTER TABLE farm_users
    ADD CONSTRAINT fk_farm_users_user_id FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE farm_users
    ADD CONSTRAINT uk_farm_users_farm_id_user_id UNIQUE (farm_id, user_id);

CREATE INDEX idx_farm_users_farm_id ON farm_users (farm_id);
CREATE INDEX idx_farm_users_user_id ON farm_users (user_id);
CREATE INDEX idx_farm_users_role ON farm_users (role);
