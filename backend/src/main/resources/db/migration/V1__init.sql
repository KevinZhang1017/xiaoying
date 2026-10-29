-- 兼容 MySQL 8 与 H2 (MODE=MySQL)，便于集成测试

CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(191) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

-- 会话：仅保存 token 的 SHA-256，原始 token 只存在于 HttpOnly Cookie
CREATE TABLE user_session (
    token_hash CHAR(64)    NOT NULL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX idx_session_user ON user_session (user_id);

CREATE TABLE category (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0
);

CREATE TABLE template (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    title                VARCHAR(128) NOT NULL,
    category_id          VARCHAR(64)  NOT NULL,
    cover_url            VARCHAR(255) NOT NULL,
    description          VARCHAR(512) NOT NULL,
    required_image_count INT          NOT NULL,
    -- 逗号分隔，如 image/jpeg,image/png
    accepted_mime_types  VARCHAR(255) NOT NULL,
    max_file_size_bytes  BIGINT       NOT NULL,
    sort_order           INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_template_category FOREIGN KEY (category_id) REFERENCES category (id)
);
CREATE INDEX idx_template_category ON template (category_id, sort_order);

CREATE TABLE asset (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    storage_path      VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(64)  NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    created_at        DATETIME(3)  NOT NULL,
    CONSTRAINT fk_asset_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX idx_asset_user ON asset (user_id);

-- status: PENDING / RUNNING / SUCCEEDED / FAILED
-- stage : ASSET_CHECK / GENERATE / FINALIZE（当前阶段或失败阶段）
CREATE TABLE task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    template_id   VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    stage         VARCHAR(16)  NULL,
    error_code    VARCHAR(64)  NULL,
    error_message VARCHAR(512) NULL,
    result_path   VARCHAR(255) NULL,
    result_url    VARCHAR(255) NULL,
    sim_config    TEXT         NULL,
    created_at    DATETIME(3)  NOT NULL,
    started_at    DATETIME(3)  NULL,
    finished_at   DATETIME(3)  NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES template (id)
);
CREATE INDEX idx_task_user_created ON task (user_id, id);
CREATE INDEX idx_task_user_template ON task (user_id, template_id, id);
CREATE INDEX idx_task_status ON task (status);

CREATE TABLE task_asset (
    task_id    BIGINT NOT NULL,
    slot_index INT    NOT NULL,
    asset_id   BIGINT NOT NULL,
    PRIMARY KEY (task_id, slot_index),
    CONSTRAINT fk_task_asset_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_asset_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
);

-- 每个步骤独立的执行记录：可观察的执行边界
CREATE TABLE task_step_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(16)  NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    message    VARCHAR(512) NULL,
    started_at DATETIME(3)  NOT NULL,
    ended_at   DATETIME(3)  NULL,
    CONSTRAINT fk_step_task FOREIGN KEY (task_id) REFERENCES task (id)
);
CREATE INDEX idx_step_task ON task_step_log (task_id, id);
