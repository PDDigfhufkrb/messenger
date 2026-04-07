import asyncio
import json
import sqlite3
import hashlib
import hmac
import secrets
import base64
import os
import uuid
import time
from datetime import datetime
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2
from cryptography.hazmat.primitives import hashes
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
import aiofiles

app = FastAPI()

# ============================================
# ШИФРОВАНИЕ (AES-GCM)
# ============================================

def derive_key(password: str, salt: bytes) -> bytes:
    """Получение ключа из пароля (для чатов)"""
    kdf = PBKDF2(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=100000,
    )
    return kdf.derive(password.encode())

def encrypt_message(message: str, key: bytes) -> dict:
    """Шифрование сообщения"""
    iv = secrets.token_bytes(12)
    cipher = Cipher(algorithms.AES(key), modes.GCM(iv))
    encryptor = cipher.encryptor()
    ciphertext = encryptor.update(message.encode()) + encryptor.finalize()
    return {
        "ciphertext": base64.b64encode(ciphertext).decode(),
        "iv": base64.b64encode(iv).decode(),
        "tag": base64.b64encode(encryptor.tag).decode()
    }

def decrypt_message(encrypted: dict, key: bytes) -> str:
    """Расшифровка сообщения"""
    ciphertext = base64.b64decode(encrypted["ciphertext"])
    iv = base64.b64decode(encrypted["iv"])
    tag = base64.b64decode(encrypted["tag"])
    cipher = Cipher(algorithms.AES(key), modes.GCM(iv, tag))
    decryptor = cipher.decryptor()
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()
    return plaintext.decode()

def generate_chat_key(chat_id: str, master_key: bytes = None) -> bytes:
    """Генерация уникального ключа для чата"""
    if master_key is None:
        master_key = base64.b64decode(os.environ.get("MASTER_KEY", base64.b64encode(secrets.token_bytes(32)).decode()))
    return hashlib.pbkdf2_hmac('sha256', chat_id.encode(), master_key, 100000, dklen=32)

# ============================================
# БАЗА ДАННЫХ
# ============================================

def init_db():
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    
    # Пользователи
    c.execute('''
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT UNIQUE NOT NULL,
            username TEXT UNIQUE NOT NULL,
            display_name TEXT,
            avatar TEXT,
            avatar_color TEXT DEFAULT '#E8F0FE',
            bio TEXT,
            public_key TEXT,
            created_at INTEGER NOT NULL,
            last_seen INTEGER,
            settings TEXT DEFAULT '{}'
        )
    ''')
    
    # Чаты
    c.execute('''
        CREATE TABLE IF NOT EXISTS chats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id TEXT UNIQUE NOT NULL,
            type TEXT NOT NULL,
            title TEXT NOT NULL,
            avatar TEXT,
            created_by TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_message TEXT,
            last_message_time INTEGER,
            encrypted_key TEXT
        )
    ''')
    
    # Участники чатов
    c.execute('''
        CREATE TABLE IF NOT EXISTS chat_members (
            chat_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            role TEXT DEFAULT 'member',
            joined_at INTEGER NOT NULL,
            last_read INTEGER,
            UNIQUE(chat_id, user_id)
        )
    ''')
    
    # Сообщения (зашифрованные)
    c.execute('''
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message_id TEXT UNIQUE NOT NULL,
            chat_id TEXT NOT NULL,
            from_user TEXT NOT NULL,
            encrypted_text TEXT,
            encrypted_data TEXT,
            timestamp INTEGER NOT NULL,
            edited_at INTEGER,
            deleted_at INTEGER,
            reply_to TEXT,
            is_media INTEGER DEFAULT 0,
            media_type TEXT,
            media_path TEXT
        )
    ''')
    
    # Индексы для скорости
    c.execute('CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id, timestamp)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_members_user ON chat_members(user_id)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)')
    
    # FTS для поиска
    c.execute('CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(encrypted_text, content=messages, content_rowid=id)')
    
    conn.commit()
    conn.close()

init_db()

# ============================================
# ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
# ============================================

def generate_id() -> str:
    """Генерация уникального ID"""
    return secrets.token_hex(8)

def now() -> int:
    """Текущее время в секундах"""
    return int(time.time())

def create_user(username: str, display_name: str = None) -> dict:
    """Создание нового пользователя"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    user_id = generate_id()
    try:
        c.execute('''
            INSERT INTO users (user_id, username, display_name, created_at, last_seen, settings)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (user_id, username.lower(), display_name or username, now(), now(), '{}'))
        conn.commit()
        return {"success": True, "user_id": user_id, "username": username}
    except sqlite3.IntegrityError:
        return {"success": False, "error": "Username already exists"}
    finally:
        conn.close()

def get_user_by_username(username: str) -> dict:
    """Получение пользователя по имени"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    c.execute('SELECT user_id, username, display_name, avatar, avatar_color, bio, last_seen, settings FROM users WHERE username = ?', (username.lower(),))
    row = c.fetchone()
    conn.close()
    if row:
        return {
            "user_id": row[0], "username": row[1], "display_name": row[2],
            "avatar": row[3], "avatar_color": row[4], "bio": row[5],
            "last_seen": row[6], "settings": json.loads(row[7] or '{}')
        }
    return None

def create_chat(chat_type: str, title: str, created_by: str, members: list, avatar: str = None) -> dict:
    """Создание нового чата"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    chat_id = generate_id()
    now_ts = now()
    
    c.execute('''
        INSERT INTO chats (chat_id, type, title, avatar, created_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
    ''', (chat_id, chat_type, title, avatar, created_by, now_ts))
    
    # Добавляем создателя
    c.execute('INSERT INTO chat_members (chat_id, user_id, role, joined_at, last_read) VALUES (?, ?, ?, ?, ?)',
              (chat_id, created_by, 'owner', now_ts, now_ts))
    
    # Добавляем остальных участников
    for member in members:
        if member != created_by:
            c.execute('INSERT INTO chat_members (chat_id, user_id, role, joined_at, last_read) VALUES (?, ?, ?, ?, ?)',
                      (chat_id, member, 'member', now_ts, now_ts))
    
    conn.commit()
    conn.close()
    return {"chat_id": chat_id, "title": title}

def get_user_chats(user_id: str) -> list:
    """Получение всех чатов пользователя"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    c.execute('''
        SELECT c.chat_id, c.type, c.title, c.avatar, c.last_message, c.last_message_time,
               (SELECT COUNT(*) FROM unread WHERE user_id = ? AND chat_id = c.chat_id) as unread
        FROM chats c
        JOIN chat_members cm ON c.chat_id = cm.chat_id
        WHERE cm.user_id = ?
        ORDER BY c.last_message_time DESC NULLS LAST
    ''', (user_id, user_id))
    rows = c.fetchall()
    conn.close()
    return [{
        "chat_id": r[0], "type": r[1], "title": r[2], "avatar": r[3],
        "last_message": r[4], "last_message_time": r[5], "unread": r[6] or 0
    } for r in rows]

def save_message(chat_id: str, from_user: str, text: str, chat_key: bytes, reply_to: str = None) -> str:
    """Сохранение зашифрованного сообщения"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    message_id = generate_id()
    now_ts = now()
    
    # Шифруем сообщение
    encrypted = encrypt_message(text, chat_key)
    encrypted_json = json.dumps(encrypted)
    
    c.execute('''
        INSERT INTO messages (message_id, chat_id, from_user, encrypted_data, timestamp, reply_to)
        VALUES (?, ?, ?, ?, ?, ?)
    ''', (message_id, chat_id, from_user, encrypted_json, now_ts, reply_to))
    
    # Обновляем последнее сообщение в чате
    c.execute('UPDATE chats SET last_message = ?, last_message_time = ? WHERE chat_id = ?',
              (text[:50], now_ts, chat_id))
    
    conn.commit()
    conn.close()
    return message_id

def get_chat_messages(chat_id: str, chat_key: bytes, limit: int = 50, offset: int = 0) -> list:
    """Получение и расшифровка сообщений чата"""
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    c.execute('''
        SELECT message_id, from_user, encrypted_data, timestamp, edited_at, reply_to,
               is_media, media_type, media_path
        FROM messages 
        WHERE chat_id = ? AND deleted_at IS NULL
        ORDER BY timestamp DESC LIMIT ? OFFSET ?
    ''', (chat_id, limit, offset))
    rows = c.fetchall()
    conn.close()
    
    messages = []
    for row in rows:
        try:
            encrypted = json.loads(row[2])
            text = decrypt_message(encrypted, chat_key)
            messages.append({
                "id": row[0], "from": row[1], "text": text,
                "timestamp": row[3], "edited": row[4] is not None,
                "reply_to": row[5], "is_media": row[6], "media_type": row[7], "media_path": row[8]
            })
        except:
            messages.append({
                "id": row[0], "from": row[1], "text": "[Зашифрованное сообщение]",
                "timestamp": row[3], "edited": False, "reply_to": row[5]
            })
    return messages

# ============================================
# WEBSOCKET МЕНЕДЖЕР
# ============================================

class ConnectionManager:
    def __init__(self):
        self.connections: dict[str, WebSocket] = {}
        self.user_map: dict[str, str] = {}  # username -> user_id
    
    async def connect(self, websocket: WebSocket, user_id: str, username: str):
        await websocket.accept()
        self.connections[user_id] = websocket
        self.user_map[username] = user_id
        await self.broadcast_users()
    
    def disconnect(self, user_id: str):
        if user_id in self.connections:
            del self.connections[user_id]
    
    async def send_to_user(self, user_id: str, data: dict):
        if user_id in self.connections:
            try:
                await self.connections[user_id].send_json(data)
            except:
                pass
    
    async def send_to_username(self, username: str, data: dict):
        if username in self.user_map:
            await self.send_to_user(self.user_map[username], data)
    
    async def broadcast_to_chat(self, chat_id: str, data: dict, exclude_user_id: str = None):
        conn = sqlite3.connect('hemax.db')
        c = conn.cursor()
        c.execute('SELECT user_id FROM chat_members WHERE chat_id = ?', (chat_id,))
        members = [row[0] for row in c.fetchall()]
        conn.close()
        for member_id in members:
            if member_id != exclude_user_id and member_id in self.connections:
                try:
                    await self.connections[member_id].send_json(data)
                except:
                    pass
    
    async def broadcast_users(self):
        users = list(self.user_map.keys())
        for user_id, ws in self.connections.items():
            try:
                await ws.send_json({"type": "users", "users": users})
            except:
                pass

manager = ConnectionManager()

# ============================================
# HTTP МАРШРУТЫ
# ============================================

@app.get("/")
async def get_index():
    async with aiofiles.open("index.html", encoding="utf-8") as f:
        return HTMLResponse(await f.read())

@app.post("/api/register")
async def register(request: Request):
    data = await request.json()
    username = data.get("username", "").strip().lower()
    display_name = data.get("display_name", username)
    
    if len(username) < 2 or len(username) > 20:
        return JSONResponse({"success": False, "error": "Имя должно быть 2-20 символов"})
    
    result = create_user(username, display_name)
    return JSONResponse(result)

@app.get("/api/users/search")
async def search_users(q: str):
    conn = sqlite3.connect('hemax.db')
    c = conn.cursor()
    c.execute('SELECT username, display_name, avatar_color FROM users WHERE username LIKE ? LIMIT 20', (f'%{q}%',))
    users = [{"username": r[0], "display_name": r[1], "avatar_color": r[2]} for r in c.fetchall()]
    conn.close()
    return JSONResponse(users)

@app.post("/api/chats/create")
async def create_chat_api(request: Request):
    data = await request.json()
    chat_type = data.get("type", "private")
    title = data.get("title", "Новый чат")
    created_by = data.get("created_by")
    members = data.get("members", [])
    
    if created_by not in members:
        members.append(created_by)
    
    result = create_chat(chat_type, title, created_by, members)
    return JSONResponse(result)

# ============================================
# WEBSOCKET ЭНДПОИНТ
# ============================================

@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
    user = get_user_by_username(username)
    if not user:
        await websocket.accept()
        await websocket.send_json({"type": "error", "message": "User not found"})
        await websocket.close()
        return
    
    user_id = user["user_id"]
    await manager.connect(websocket, user_id, username)
    
    # Отправляем список чатов
    chats = get_user_chats(user_id)
    await websocket.send_json({"type": "chats", "chats": chats})
    
    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            msg_type = msg.get("type")
            
            if msg_type == "join_chat":
                chat_id = msg.get("chat_id")
                # Отправляем историю чата
                chat_key = generate_chat_key(chat_id)
                messages = get_chat_messages(chat_id, chat_key, limit=50)
                await websocket.send_json({"type": "history", "chat_id": chat_id, "messages": messages})
            
            elif msg_type == "send_message":
                chat_id = msg.get("chat_id")
                text = msg.get("text", "")
                reply_to = msg.get("reply_to")
                chat_key = generate_chat_key(chat_id)
                
                message_id = save_message(chat_id, user_id, text, chat_key, reply_to)
                
                # Рассылаем участникам чата
                await manager.broadcast_to_chat(chat_id, {
                    "type": "new_message",
                    "chat_id": chat_id,
                    "message": {
                        "id": message_id,
                        "from": username,
                        "text": text,
                        "timestamp": now(),
                        "reply_to": reply_to
                    }
                }, exclude_user_id=user_id)
                
                # Подтверждаем отправителю
                await websocket.send_json({
                    "type": "message_sent",
                    "message_id": message_id,
                    "chat_id": chat_id
                })
            
            elif msg_type == "typing":
                chat_id = msg.get("chat_id")
                await manager.broadcast_to_chat(chat_id, {
                    "type": "typing",
                    "chat_id": chat_id,
                    "from": username
                }, exclude_user_id=user_id)
            
            elif msg_type == "read":
                chat_id = msg.get("chat_id")
                conn = sqlite3.connect('hemax.db')
                c = conn.cursor()
                c.execute('UPDATE chat_members SET last_read = ? WHERE chat_id = ? AND user_id = ?',
                          (now(), chat_id, user_id))
                conn.commit()
                conn.close()
            
            elif msg_type == "get_chats":
                chats = get_user_chats(user_id)
                await websocket.send_json({"type": "chats", "chats": chats})
                
    except WebSocketDisconnect:
        manager.disconnect(user_id)
        await manager.broadcast_users()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
