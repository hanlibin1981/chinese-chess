-- 中国象棋游戏数据库初始化脚本 (MySQL)

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    win_count INT DEFAULT 0,
    lose_count INT DEFAULT 0,
    draw_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对局表 (移除外键约束，允许 player_black 为 NULL 表示 AI)
CREATE TABLE IF NOT EXISTS games (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_red BIGINT,
    player_black BIGINT,
    winner VARCHAR(10),
    pgn TEXT,
    is_ai BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'waiting',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 索引 (使用 DROP INDEX 先删除再创建)
DROP INDEX IF EXISTS idx_games_status ON games;
DROP INDEX IF EXISTS idx_games_player ON games;
CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_player ON games(player_red, player_black);

-- 默认测试用户 (用户名: hlb, 密码: 111111)
INSERT INTO users(username, password, win_count, lose_count, draw_count) 
VALUES('hlb', '96e79218965eb72c92a549dd5a330112', 0, 0, 0)
ON DUPLICATE KEY UPDATE username=username;
