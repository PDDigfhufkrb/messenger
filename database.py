# database.py - Полная схема базы данных HEmax
# Поддерживает: пользователи, чаты, сообщения, медиа, реакции, настройки, бэкапы

import sqlite3
import json
import os
from datetime import datetime
from pathlib import Path

DB_PATH = "hemax.db"

def get_connection():
    """Возвращает соединение с БД с включёнными foreign keys"""
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")  # Улучшает производительность при записи
    conn.row_factory = sqlite3.Row
    return conn

def init_database():
    """Инициализация всей схемы базы данных"""
    conn = get_connection()
    c = conn.cursor()
    
    # ============================================
    # 1. ПОЛЬЗОВАТЕЛИ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL COLLATE NOCASE,
            display_name TEXT,
            avatar TEXT,                      -- путь к аватару или base64
            avatar_color TEXT DEFAULT '#0a0a0a',
            bio TEXT,
            phone TEXT,
            email TEXT,
            is_online INTEGER DEFAULT 0,
            last_seen INTEGER,
            created_at INTEGER NOT NULL,
            settings TEXT DEFAULT '{}',       -- JSON с настройками
            is_deleted INTEGER DEFAULT 0
        )
    ''')
    
    # Индексы для users
    c.execute('CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(last_seen)')
    
    # ============================================
    # 2. ЧАТЫ (групповые и личные)
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS chats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id TEXT UNIQUE NOT NULL,     -- UUID чата
            type TEXT NOT NULL,               -- 'private', 'group', 'channel'
            title TEXT,
            avatar TEXT,
            created_by TEXT,
            created_at INTEGER NOT NULL,
            last_message TEXT,
            last_message_time INTEGER,
            is_archived INTEGER DEFAULT 0,
            is_muted INTEGER DEFAULT 0,
            settings TEXT DEFAULT '{}'
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_chats_chat_id ON chats(chat_id)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_chats_last_message ON chats(last_message_time)')
    
    # ============================================
    # 3. УЧАСТНИКИ ЧАТОВ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS chat_members (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id TEXT NOT NULL,
            username TEXT NOT NULL,
            role TEXT DEFAULT 'member',       -- 'owner', 'admin', 'member'
            joined_at INTEGER NOT NULL,
            last_read_time INTEGER,
            muted_until INTEGER DEFAULT 0,
            FOREIGN KEY (chat_id) REFERENCES chats(chat_id),
            FOREIGN KEY (username) REFERENCES users(username),
            UNIQUE(chat_id, username)
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_members_chat ON chat_members(chat_id)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_members_user ON chat_members(username)')
    
    # ============================================
    # 4. СООБЩЕНИЯ (основная таблица)
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id TEXT UNIQUE NOT NULL,
            chat_id TEXT NOT NULL,
            from_user TEXT NOT NULL,
            reply_to TEXT,
            text TEXT,
            formatted_text TEXT,
            timestamp INTEGER NOT NULL,
            edited_at INTEGER,
            deleted_at INTEGER,
            is_edited INTEGER DEFAULT 0,
            is_deleted INTEGER DEFAULT 0,
            forward_from TEXT,
            view_count INTEGER DEFAULT 0,
            FOREIGN KEY (chat_id) REFERENCES chats(chat_id),
            FOREIGN KEY (from_user) REFERENCES users(username),
            FOREIGN KEY (reply_to) REFERENCES messages(message_id)
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_messages_chat_time ON messages(chat_id, timestamp)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_messages_from_user ON messages(from_user)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)')
    
    # ============================================
    # 5. FTS5 (полнотекстовый поиск)
    # ============================================
    c.execute('''
        CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts 
        USING fts5(text, content=messages, content_rowid=id, tokenize='porter unicode61')
    ''')
    
    # Триггеры для автоматического обновления FTS
    c.execute('''
        CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
            INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text);
        END
    ''')
    
    c.execute('''
        CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
            INSERT INTO messages_fts(messages_fts, rowid, text) VALUES('delete', old.id, old.text);
        END
    ''')
    
    c.execute('''
        CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
            INSERT INTO messages_fts(messages_fts, rowid, text) VALUES('delete', old.id, old.text);
            INSERT INTO messages_fts(rowid, text) VALUES (new.id, new.text);
        END
    ''')
    
    # ============================================
    # 6. МЕДИАФАЙЛЫ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS media (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            media_id TEXT UNIQUE NOT NULL,
            message_id TEXT NOT NULL,
            chat_id TEXT NOT NULL,
            media_type TEXT NOT NULL,         -- 'image', 'video', 'audio', 'file', 'voice'
            file_path TEXT NOT NULL,
            file_size INTEGER,
            thumb_path TEXT,
            width INTEGER,
            height INTEGER,
            duration INTEGER,
            mime_type TEXT,
            uploaded_at INTEGER NOT NULL,
            FOREIGN KEY (message_id) REFERENCES messages(message_id),
            FOREIGN KEY (chat_id) REFERENCES chats(chat_id)
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_media_message ON media(message_id)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_media_chat ON media(chat_id)')
    
    # ============================================
    # 7. РЕАКЦИИ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS reactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id TEXT NOT NULL,
            username TEXT NOT NULL,
            reaction TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            FOREIGN KEY (message_id) REFERENCES messages(message_id),
            FOREIGN KEY (username) REFERENCES users(username),
            UNIQUE(message_id, username)
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_reactions_message ON reactions(message_id)')
    
    # ============================================
    # 8. НЕПРОЧИТАННЫЕ СООБЩЕНИЯ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS unread (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            chat_id TEXT NOT NULL,
            count INTEGER DEFAULT 0,
            last_message_id TEXT,
            FOREIGN KEY (username) REFERENCES users(username),
            FOREIGN KEY (chat_id) REFERENCES chats(chat_id),
            UNIQUE(username, chat_id)
        )
    ''')
    
    c.execute('CREATE INDEX IF NOT EXISTS idx_unread_user ON unread(username)')
    
    # ============================================
    # 9. ЧЕРНОВИКИ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS drafts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            chat_id TEXT NOT NULL,
            text TEXT,
            reply_to TEXT,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY (username) REFERENCES users(username),
            FOREIGN KEY (chat_id) REFERENCES chats(chat_id),
            UNIQUE(username, chat_id)
        )
    ''')
    
    # ============================================
    # 10. БЭКАПЫ СООБЩЕНИЙ (для восстановления)
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS message_backups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id TEXT NOT NULL,
            backup_data TEXT NOT NULL,        -- JSON с полным сообщением
            backup_time INTEGER NOT NULL,
            restored INTEGER DEFAULT 0
        )
    ''')
    
    # ============================================
    # 11. ПИН-КОД И БЕЗОПАСНОСТЬ
    # ============================================
    c.execute('''
        CREATE TABLE IF NOT EXISTS security (
            username TEXT PRIMARY KEY,
            pin_code TEXT,                    -- хешированный PIN
            biometric_enabled INTEGER DEFAULT 0,
            last_auth INTEGER,
            failed_attempts INTEGER DEFAULT 0,
            locked_until INTEGER DEFAULT 0,
            FOREIGN KEY (username) REFERENCES users(username)
        )
    ''')
    
    conn.commit()
    conn.close()

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С ПОЛЬЗОВАТЕЛЯМИ
# ============================================

def create_user(username: str, display_name: str = None, avatar_color: str = '#0a0a0a') -> dict:
    """Создание нового пользователя"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    
    try:
        c.execute('''
            INSERT INTO users (username, display_name, avatar_color, created_at, last_seen, settings)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (username, display_name or username, avatar_color, now, now, '{}'))
        conn.commit()
        
        # Создаём личный чат "Заметки" для пользователя
        create_private_chat(username, username, "Мои заметки")
        
        return {"success": True, "username": username}
    except sqlite3.IntegrityError:
        return {"success": False, "error": "Username already exists"}
    finally:
        conn.close()

def get_user(username: str) -> dict:
    """Получение информации о пользователе"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('SELECT * FROM users WHERE username = ? AND is_deleted = 0', (username,))
    row = c.fetchone()
    conn.close()
    return dict(row) if row else None

def update_user_settings(username: str, settings: dict) -> bool:
    """Обновление настроек пользователя"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('UPDATE users SET settings = ? WHERE username = ?', (json.dumps(settings), username))
    conn.commit()
    conn.close()
    return True

def update_avatar(username: str, avatar_data: str) -> bool:
    """Обновление аватара пользователя (base64 или путь)"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('UPDATE users SET avatar = ? WHERE username = ?', (avatar_data, username))
    conn.commit()
    conn.close()
    return True

def update_last_seen(username: str) -> None:
    """Обновление времени последнего визита"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('UPDATE users SET last_seen = ? WHERE username = ?', (int(datetime.now().timestamp()), username))
    conn.commit()
    conn.close()

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С ЧАТАМИ
# ============================================

import uuid

def create_chat(chat_type: str, title: str, created_by: str, members: list = None, avatar: str = None) -> dict:
    """Создание нового чата (группового или канала)"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    chat_id = str(uuid.uuid4())[:8]
    
    c.execute('''
        INSERT INTO chats (chat_id, type, title, created_by, created_at, avatar)
        VALUES (?, ?, ?, ?, ?, ?)
    ''', (chat_id, chat_type, title, created_by, now, avatar))
    
    # Добавляем создателя как владельца
    c.execute('''
        INSERT INTO chat_members (chat_id, username, role, joined_at, last_read_time)
        VALUES (?, ?, ?, ?, ?)
    ''', (chat_id, created_by, 'owner', now, now))
    
    # Добавляем остальных участников
    if members:
        for member in members:
            if member != created_by:
                c.execute('''
                    INSERT INTO chat_members (chat_id, username, role, joined_at, last_read_time)
                    VALUES (?, ?, ?, ?, ?)
                ''', (chat_id, member, 'member', now, now))
    
    conn.commit()
    conn.close()
    return {"chat_id": chat_id, "title": title}

def create_private_chat(username1: str, username2: str, title: str = None) -> dict:
    """Создание личного чата между двумя пользователями"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    
    # Генерируем уникальный ID для личного чата
    chat_id = str(uuid.uuid4())[:8]
    chat_title = title or f"{username1}_{username2}"
    
    c.execute('''
        INSERT INTO chats (chat_id, type, title, created_by, created_at)
        VALUES (?, ?, ?, ?, ?)
    ''', (chat_id, 'private', chat_title, username1, now))
    
    c.execute('''
        INSERT INTO chat_members (chat_id, username, role, joined_at, last_read_time)
        VALUES (?, ?, ?, ?, ?)
    ''', (chat_id, username1, 'member', now, now))
    
    c.execute('''
        INSERT INTO chat_members (chat_id, username, role, joined_at, last_read_time)
        VALUES (?, ?, ?, ?, ?)
    ''', (chat_id, username2, 'member', now, now))
    
    conn.commit()
    conn.close()
    return {"chat_id": chat_id, "title": chat_title}

def get_user_chats(username: str) -> list:
    """Получение всех чатов пользователя"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('''
        SELECT c.*, 
               (SELECT count FROM unread WHERE username = ? AND chat_id = c.chat_id) as unread_count
        FROM chats c
        JOIN chat_members cm ON c.chat_id = cm.chat_id
        WHERE cm.username = ? AND c.is_archived = 0
        ORDER BY c.last_message_time DESC NULLS LAST
    ''', (username, username))
    rows = c.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def add_member_to_chat(chat_id: str, username: str, role: str = 'member') -> bool:
    """Добавление участника в чат"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    try:
        c.execute('''
            INSERT INTO chat_members (chat_id, username, role, joined_at, last_read_time)
            VALUES (?, ?, ?, ?, ?)
        ''', (chat_id, username, role, now, now))
        conn.commit()
        return True
    except sqlite3.IntegrityError:
        return False
    finally:
        conn.close()

def remove_member_from_chat(chat_id: str, username: str) -> bool:
    """Удаление участника из чата"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('DELETE FROM chat_members WHERE chat_id = ? AND username = ?', (chat_id, username))
    conn.commit()
    conn.close()
    return True

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С СООБЩЕНИЯМИ
# ============================================

def save_message(chat_id: str, from_user: str, text: str, reply_to: str = None, 
                 formatted_text: str = None) -> str:
    """Сохранение сообщения в БД"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    message_id = str(uuid.uuid4())[:16]
    
    c.execute('''
        INSERT INTO messages (message_id, chat_id, from_user, text, formatted_text, timestamp, reply_to)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    ''', (message_id, chat_id, from_user, text, formatted_text or text, now, reply_to))
    
    # Обновляем last_message в чате
    c.execute('''
        UPDATE chats SET last_message = ?, last_message_time = ?
        WHERE chat_id = ?
    ''', (text[:100] if text else '[Медиа]', now, chat_id))
    
    conn.commit()
    conn.close()
    return message_id

def get_chat_messages(chat_id: str, limit: int = 50, offset: int = 0) -> list:
    """Получение сообщений чата с пагинацией"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('''
        SELECT m.*, 
               (SELECT json_group_array(json_object('user', r.username, 'reaction', r.reaction))
                FROM reactions r WHERE r.message_id = m.message_id) as reactions
        FROM messages m
        WHERE m.chat_id = ? AND m.is_deleted = 0
        ORDER BY m.timestamp DESC LIMIT ? OFFSET ?
    ''', (chat_id, limit, offset))
    rows = c.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def search_in_chat(chat_id: str, query: str, limit: int = 50) -> list:
    """Полнотекстовый поиск по чату"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('''
        SELECT m.message_id, m.from_user, m.text, m.timestamp
        FROM messages_fts fts
        JOIN messages m ON fts.rowid = m.id
        WHERE messages_fts MATCH ? AND m.chat_id = ?
        ORDER BY m.timestamp DESC LIMIT ?
    ''', (query, chat_id, limit))
    rows = c.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def edit_message(message_id: str, new_text: str, new_formatted: str = None) -> bool:
    """Редактирование сообщения"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    c.execute('''
        UPDATE messages 
        SET text = ?, formatted_text = ?, edited_at = ?, is_edited = 1
        WHERE message_id = ?
    ''', (new_text, new_formatted or new_text, now, message_id))
    conn.commit()
    conn.close()
    return True

def delete_message(message_id: str, soft_delete: bool = True) -> bool:
    """Удаление сообщения (мягкое или полное)"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    if soft_delete:
        c.execute('UPDATE messages SET deleted_at = ?, is_deleted = 1 WHERE message_id = ?', (now, message_id))
    else:
        # Полное удаление
        c.execute('DELETE FROM messages WHERE message_id = ?', (message_id,))
        c.execute('DELETE FROM messages_fts WHERE rowid IN (SELECT id FROM messages WHERE message_id = ?)', (message_id,))
    conn.commit()
    conn.close()
    return True

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С РЕАКЦИЯМИ
# ============================================

def add_reaction(message_id: str, username: str, reaction: str) -> bool:
    """Добавление или обновление реакции"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    c.execute('''
        INSERT OR REPLACE INTO reactions (message_id, username, reaction, timestamp)
        VALUES (?, ?, ?, ?)
    ''', (message_id, username, reaction, now))
    conn.commit()
    conn.close()
    return True

def remove_reaction(message_id: str, username: str) -> bool:
    """Удаление реакции"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('DELETE FROM reactions WHERE message_id = ? AND username = ?', (message_id, username))
    conn.commit()
    conn.close()
    return True

def get_message_reactions(message_id: str) -> list:
    """Получение всех реакций на сообщение"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('SELECT username, reaction FROM reactions WHERE message_id = ?', (message_id,))
    rows = c.fetchall()
    conn.close()
    return [{"user": row[0], "reaction": row[1]} for row in rows]

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С НЕПРОЧИТАННЫМИ
# ============================================

def increment_unread(username: str, chat_id: str, message_id: str = None) -> None:
    """Увеличение счётчика непрочитанных"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('''
        INSERT INTO unread (username, chat_id, count, last_message_id)
        VALUES (?, ?, 1, ?)
        ON CONFLICT(username, chat_id) DO UPDATE SET 
            count = count + 1,
            last_message_id = COALESCE(?, last_message_id)
    ''', (username, chat_id, message_id, message_id))
    conn.commit()
    conn.close()

def clear_unread(username: str, chat_id: str) -> None:
    """Очистка счётчика непрочитанных"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('DELETE FROM unread WHERE username = ? AND chat_id = ?', (username, chat_id))
    conn.commit()
    conn.close()

def get_unread_counts(username: str) -> dict:
    """Получение всех непрочитанных для пользователя"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('SELECT chat_id, count FROM unread WHERE username = ?', (username,))
    rows = c.fetchall()
    conn.close()
    return {row[0]: row[1] for row in rows}

# ============================================
# ФУНКЦИИ ДЛЯ РАБОТЫ С ЧЕРНОВИКАМИ
# ============================================

def save_draft(username: str, chat_id: str, text: str, reply_to: str = None) -> None:
    """Сохранение черновика сообщения"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    c.execute('''
        INSERT OR REPLACE INTO drafts (username, chat_id, text, reply_to, updated_at)
        VALUES (?, ?, ?, ?, ?)
    ''', (username, chat_id, text, reply_to, now))
    conn.commit()
    conn.close()

def get_draft(username: str, chat_id: str) -> dict:
    """Получение черновика для чата"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('SELECT text, reply_to FROM drafts WHERE username = ? AND chat_id = ?', (username, chat_id))
    row = c.fetchone()
    conn.close()
    return {"text": row[0], "reply_to": row[1]} if row else None

# ============================================
# ФУНКЦИИ ДЛЯ БЭКАПОВ
# ============================================

def backup_message(message_id: str, message_data: dict) -> None:
    """Создание бэкапа сообщения"""
    conn = get_connection()
    c = conn.cursor()
    now = int(datetime.now().timestamp())
    c.execute('''
        INSERT INTO message_backups (message_id, backup_data, backup_time)
        VALUES (?, ?, ?)
    ''', (message_id, json.dumps(message_data), now))
    conn.commit()
    conn.close()

# ============================================
# ФУНКЦИИ ДЛЯ PIN-КОДА
# ============================================

def set_pin_code(username: str, pin_hash: str) -> bool:
    """Установка PIN-кода"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('''
        INSERT OR REPLACE INTO security (username, pin_code, last_auth, failed_attempts)
        VALUES (?, ?, ?, 0)
    ''', (username, pin_hash, int(datetime.now().timestamp())))
    conn.commit()
    conn.close()
    return True

def verify_pin_code(username: str, pin_hash: str) -> bool:
    """Проверка PIN-кода"""
    conn = get_connection()
    c = conn.cursor()
    c.execute('SELECT pin_code, failed_attempts, locked_until FROM security WHERE username = ?', (username,))
    row = c.fetchone()
    conn.close()
    if not row:
        return False
    if row[2] and row[2] > int(datetime.now().timestamp()):
        return False
    if row[0] == pin_hash:
        c.execute('UPDATE security SET failed_attempts = 0, last_auth = ? WHERE username = ?', 
                  (int(datetime.now().timestamp()), username))
        return True
    else:
        new_attempts = (row[1] or 0) + 1
        locked_until = int(datetime.now().timestamp()) + 300 if new_attempts >= 5 else 0
        c.execute('UPDATE security SET failed_attempts = ?, locked_until = ? WHERE username = ?',
                  (new_attempts, locked_until, username))
        return False

# Инициализация при импорте
init_database()
