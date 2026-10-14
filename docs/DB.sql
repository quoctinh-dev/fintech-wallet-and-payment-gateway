-- ==============================================================================
-- KIẾN TRÚC CƠ SỞ DỮ LIỆU MICROSERVICES (DATABASE-PER-SERVICE PATTERN)
-- HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (POSTGRESQL 15)
-- ==============================================================================
-- Nguyên tắc cốt lõi: Mỗi Microservice sở hữu 1 Database độc lập.
-- TUYỆT ĐỐI KHÔNG sử dụng Foreign Key chéo giữa các Database khác nhau.
-- ==============================================================================

-- ==============================================================================
-- 1. DATABASE: wallet_auth_db (Dịch vụ Xác thực & Quản trị Người dùng)
-- ==============================================================================
-- \c wallet_auth_db;

-- 1.1 Bảng USERS: Thông tin tài khoản người dùng
CREATE TABLE IF NOT EXISTS users
(
    id                   UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    phone_number         VARCHAR(15)  NOT NULL UNIQUE,
    password             VARCHAR(255) NOT NULL,
    full_name            VARCHAR(100) NOT NULL,
    status               VARCHAR(20)  NOT NULL    DEFAULT 'ACTIVE',
    citizen_id           VARCHAR(20) UNIQUE,
    id_card_front_url    TEXT,
    id_card_back_url     TEXT,
    kyc_status           VARCHAR(20)  NOT NULL    DEFAULT 'UNVERIFIED',
    kyc_rejection_reason TEXT,
    roles                VARCHAR(50)  NOT NULL    DEFAULT 'ROLE_USER',
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED')),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_user_roles CHECK (roles IN ('ROLE_USER', 'ROLE_ADMIN', 'ROLE_AUDITOR'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number ON users (phone_number);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_citizen_id ON users (citizen_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);

-- 1.2 Bảng REFRESH_TOKENS: Quản lý phiên đăng nhập dài hạn (Token Rotation)
CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    user_id     UUID                     NOT NULL,
    token       VARCHAR(255)             NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens (token);

-- 1.3 Bảng OUTBOX_EVENTS: Transactional Outbox phát sự kiện chào mừng
CREATE TABLE IF NOT EXISTS outbox_events
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',
    retry_count  INT                      DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_auth_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_auth_outbox_status ON outbox_events (status);


-- ==============================================================================
-- 2. DATABASE: wallet_account_db (Dịch vụ Quản lý Tài khoản & Ví)
-- ==============================================================================
-- \c wallet_account_db;

-- 2.1 Bảng ACCOUNTS: Thông tin ví người dùng và ví nghiệp vụ hệ thống
CREATE TABLE IF NOT EXISTS accounts
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    account_type VARCHAR(30) NOT NULL     DEFAULT 'USER_WALLET',
    currency     VARCHAR(3)  NOT NULL     DEFAULT 'VND',
    status       VARCHAR(20) NOT NULL     DEFAULT 'ACTIVE',
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_type CHECK (account_type IN ('USER_WALLET', 'SYSTEM_MASTER', 'SYSTEM_FEE', 'BANK_CLEARING',
                                                        'MERCHANT_WALLET')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts (status);


-- ==============================================================================
-- 3. DATABASE: wallet_transaction_db (Dịch vụ Xử lý Giao dịch Core Engine)
-- ==============================================================================
-- \c wallet_transaction_db;

-- 3.1 Bảng TRANSACTIONS: Quản lý trạng thái và thông tin giao dịch tài chính
CREATE TABLE IF NOT EXISTS transactions
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    account_id      UUID           NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL UNIQUE,
    description     TEXT,
    status          VARCHAR(20)    NOT NULL  DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_idempotency_key ON transactions (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions (account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions (created_at DESC);

-- 3.2 Bảng OUTBOX_EVENTS: Lưu sự kiện giao dịch để bắn sang Kafka (Zero-Loss)
CREATE TABLE IF NOT EXISTS outbox_events
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',
    retry_count  INT                      DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_tx_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_tx_outbox_status ON outbox_events (status);


-- ==============================================================================
-- 4. DATABASE: wallet_ledger_db (Dịch vụ Sổ cái Kế toán Kép Bất biến)
-- ==============================================================================
-- \c wallet_ledger_db;

-- 4.1 Bảng LEDGER_ENTRIES: Bút toán Nợ/Có bất biến (Append-Only)
-- Nguyên tắc: SUM(DEBIT) + SUM(CREDIT) = 0 trong cùng transaction_id
CREATE TABLE IF NOT EXISTS ledger_entries
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    transaction_id UUID           NOT NULL,
    account_id     UUID           NOT NULL,
    amount         NUMERIC(18, 2) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ledger_amount_not_zero CHECK (amount <> 0)
);

-- Index tối ưu hóa tính toán số dư thời gian thực và Đối soát toàn hệ thống
CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id ON ledger_entries (account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_created_at ON ledger_entries (created_at DESC);


-- ==============================================================================
-- 5. DATABASE: wallet_notification_db (Dịch vụ Nhật ký Thông báo & Audit Log)
-- ==============================================================================
-- \c wallet_notification_db;

-- 5.1 Bảng AUDIT_LOGS: Lưu vết toàn bộ sự kiện tiêu thụ từ Kafka
CREATE TABLE IF NOT EXISTS audit_logs
(
    id         UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    topic      VARCHAR(100) NOT NULL,
    payload    TEXT         NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_topic ON audit_logs (topic);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);


-- ==============================================================================
-- 6. DỮ LIỆU KHỞI TẠO HẠT GIỐNG (SEED DATA) CHO KIỂM THỬ VÀ BẢO VỆ
-- ==============================================================================

-- 6.1 Khởi tạo Tài khoản Người dùng chuẩn Doanh nghiệp (wallet_auth_db)
-- \c wallet_auth_db;
-- Mật khẩu mặc định 'Password@123': $2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6
-- Mật khẩu riêng của Bích 'NewPassword@456': $2a$10$9wFL27c51J/vXs2dHwjmIuR0q40wBCSqVS8/26HhZ1X4.I9hUQLJC
INSERT INTO users (id, phone_number, password, full_name, status, citizen_id, kyc_status, roles)
VALUES
    ('5a28ca66-31c2-4ef1-b997-4d8e6569b0e8', '0987111222', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Nguyễn Văn An', 'ACTIVE', '001099000001', 'UNVERIFIED', 'ROLE_ADMIN'),
    ('6b39da77-42d3-4fa2-c008-5e9f7670c1f9', '0987222333', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Lê Hoàng Nam', 'ACTIVE', '001099000002', 'VERIFIED', 'ROLE_ADMIN'),
    ('7c40eb88-53e4-4fb3-d119-6f0a8781d2ea', '0987333555', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Phạm Minh Đức', 'ACTIVE', '001099000003', 'VERIFIED', 'ROLE_ADMIN'),
    ('8d51fc99-64f5-4fc4-e220-7a1b9892e3fb', '0987444555', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Vũ Hải Đăng', 'ACTIVE', '001099000004', 'VERIFIED', 'ROLE_AUDITOR'),
    ('e9207688-fb90-49bc-9d9a-bf6a3c6f0d23', '0987333444', '$2a$10$9wFL27c51J/vXs2dHwjmIuR0q40wBCSqVS8/26HhZ1X4.I9hUQLJC', 'Trần Thị Bích', 'ACTIVE', '001099000005', 'VERIFIED', 'ROLE_USER'),
    ('57be392f-274b-41bd-8b3d-8fd0c912641d', '0346921795', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Hoàng Mai Linh', 'ACTIVE', NULL, 'UNVERIFIED', 'ROLE_USER'),
    ('9e62ad00-75a6-4fd5-f331-8b2ca903f4ac', '0987666777', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Đỗ Quang Huy', 'ACTIVE', '001099000007', 'PENDING', 'ROLE_USER'),
    ('b217c941-31c9-47bc-8990-0833211b4f20', '0999888777', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Nguyễn Thùy Dung', 'ACTIVE', NULL, 'UNVERIFIED', 'ROLE_USER'),
    ('af73be11-86b7-4fe6-a442-9c3db014a5bd', '0987777888', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Bùi Tuấn Anh', 'ACTIVE', '001099000009', 'VERIFIED', 'ROLE_USER'),
    ('74efed23-4c8d-4979-87e6-fc60355882cc', '0911222333', '$2a$10$erwY1NZV8FxEvUbOW4/QC.5YlkwbkVvUucc.KXGSFYyyqmlNRvzP6', 'Đặng Quốc Toàn', 'DELETED', NULL, 'UNVERIFIED', 'ROLE_USER')
ON CONFLICT (id) DO UPDATE 
SET phone_number = EXCLUDED.phone_number,
    password = EXCLUDED.password,
    full_name = EXCLUDED.full_name,
    status = EXCLUDED.status,
    kyc_status = EXCLUDED.kyc_status,
    roles = EXCLUDED.roles;

-- 6.2 Khởi tạo Két sắt Hệ thống & Tài khoản Ví người dùng (wallet_account_db)
-- \c wallet_account_db;
INSERT INTO accounts (id, user_id, account_type, currency, status)
VALUES
    -- Két sắt & Ví đối tác
    ('58b4792e-427d-437a-b2c5-0f8433fea461', '00000000-0000-0000-0000-000000000000', 'SYSTEM_MASTER', 'VND', 'ACTIVE'),
    ('3ce7a3ed-900f-4524-92ed-84e413472d8e', '00000000-0000-0000-0000-000000000000', 'BANK_CLEARING', 'VND', 'ACTIVE'),
    ('876a4a0b-261b-4fd4-9e99-68bfa5dffea2', '00000000-0000-0000-0000-000000000000', 'SYSTEM_FEE', 'VND', 'ACTIVE'),
    ('bf84cf22-97c8-4ff7-b553-0d4ec125b6ce', '00000000-0000-0000-0000-000000000000', 'MERCHANT_WALLET', 'VND', 'ACTIVE'),

    -- Ví cá nhân của người dùng (USER_WALLET)
    ('44df6843-443f-4477-a362-515830c0f1e6', '5a28ca66-31c2-4ef1-b997-4d8e6569b0e8', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('55ef7954-554f-4588-b473-626941d1e2f7', '6b39da77-42d3-4fa2-c008-5e9f7670c1f9', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('66fa8065-665a-4699-c584-737052e2e3a8', '7c40eb88-53e4-4fb3-d119-6f0a8781d2ea', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('77ab9176-776b-47aa-d695-848163f3e4b9', '8d51fc99-64f5-4fc4-e220-7a1b9892e3fb', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('2f1b118c-c82e-43e7-8241-fcae3fa1b4a9', 'e9207688-fb90-49bc-9d9a-bf6a3c6f0d23', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('f57e4912-63f2-4223-a427-c638e68a4035', '57be392f-274b-41bd-8b3d-8fd0c912641d', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('88bc0287-887c-48bb-e706-959274a4e5ca', '9e62ad00-75a6-4fd5-f331-8b2ca903f4ac', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('fbeecda9-42e2-465f-9276-07306cfc28b4', 'b217c941-31c9-47bc-8990-0833211b4f20', 'USER_WALLET', 'VND', 'ACTIVE'),
    ('99cd1398-998d-49cc-f817-060385b5e6db', 'af73be11-86b7-4fe6-a442-9c3db014a5bd', 'USER_WALLET', 'VND', 'FROZEN'),
    ('5f5b53fc-28f0-4053-b669-6029792be11b', '74efed23-4c8d-4979-87e6-fc60355882cc', 'USER_WALLET', 'VND', 'CLOSED')
ON CONFLICT (id) DO UPDATE
SET status = EXCLUDED.status;

-- 6.3 Khởi tạo Số dư Sổ cái Kế toán Kép đối ứng cân bằng 0.00 (wallet_ledger_db)
-- \c wallet_ledger_db;
-- Cấp hạn mức ban đầu cho Trần Thị Bích: 5,000,000 VND đối ứng BANK_CLEARING
INSERT INTO ledger_entries (id, transaction_id, account_id, amount)
VALUES
    ('a1111111-1111-1111-1111-111111111111', 'e0000000-0000-0000-0000-000000000001', '2f1b118c-c82e-43e7-8241-fcae3fa1b4a9', 5000000.00),
    ('a2222222-2222-2222-2222-222222222222', 'e0000000-0000-0000-0000-000000000001', '3ce7a3ed-900f-4524-92ed-84e413472d8e', -5000000.00)
ON CONFLICT (id) DO NOTHING;
