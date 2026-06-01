-- BUFF 账号身份以 buff_user_id 为准：同一个 BUFF 账号不应占用多个本地槽位。
-- MySQL 的唯一索引允许多个 NULL，因此尚未校验（buff_user_id 为空）的账号不受影响。
ALTER TABLE buff_account
    ADD CONSTRAINT uk_buff_account_buff_user_id UNIQUE (buff_user_id);
