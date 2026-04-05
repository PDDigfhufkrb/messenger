import asyncio
import json
import sqlite3
import os
from datetime import datetime
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
import aiofiles

app = FastAPI()

# --- База данных SQLite ---
def init_db():
    conn = sqlite3.connect('messages.db')
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS messages
                 (id INTEGER PRIMARY KEY AUTOINCREMENT,
                  from_user TEXT,
                  to_user TEXT,
                  text TEXT,
                  is_image INTEGER,
                  image_data TEXT,
                  timestamp TEXT,
                  deleted INTEGER DEFAULT 0)''')
    c.execute('''CREATE TABLE IF NOT EXISTS users
                 (username TEXT PRIMARY KEY,
                  last_seen TEXT)''')
    conn.commit()
    conn.close()

init_db()

def save_message(from_user, to_user, text, is_image=0, image_data=None):
    conn = sqlite3.connect('messages.db')
    c = conn.cursor()
    c.execute('''INSERT INTO messages (from_user, to_user, text, is_image, image_data, timestamp)
                 VALUES (?, ?, ?, ?, ?, ?)''',
              (from_user, to_user, text, is_image, image_data, datetime.now().isoformat()))
    conn.commit()
    conn.close()

def get_messages(user1, user2):
    conn = sqlite3.connect('messages.db')
    c = conn.cursor()
    c.execute('''SELECT from_user, text, is_image, image_data, timestamp, deleted
                 FROM messages 
                 WHERE (from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?)
                 ORDER BY id''',
              (user1, user2, user2, user1))
    messages = [{"from_user": row[0], "text": row[1], "is_image": row[2], 
                 "image_data": row[3], "timestamp": row[4], "deleted": row[5]} 
                for row in c.fetchall()]
    conn.close()
    return messages

# --- Управление соединениями ---
class ConnectionManager:
    def __init__(self):
        self.active_connections: dict[str, WebSocket] = {}  # username -> websocket

    async def connect(self, websocket: WebSocket, username: str):
        await websocket.accept()
        self.active_connections[username] = websocket
        await self.broadcast_user_list()

    def disconnect(self, username: str):
        if username in self.active_connections:
            del self.active_connections[username]

    async def broadcast_user_list(self):
        user_list = list(self.active_connections.keys())
        for username, connection in self.active_connections.items():
            try:
                await connection.send_json({
                    "type": "user_list",
                    "users": user_list
                })
            except:
                pass

    async def send_to_user(self, username: str, message: dict):
        if username in self.active_connections:
            try:
                await self.active_connections[username].send_json(message)
            except:
                pass

    async def broadcast_to_all(self, message: dict, sender: str = None):
        for username, connection in self.active_connections.items():
            if username != sender:
                try:
                    await connection.send_json(message)
                except:
                    pass

manager = ConnectionManager()

@app.get("/")
async def get():
    async with aiofiles.open("index.html", encoding="utf-8") as f:
        html_content = await f.read()
    return HTMLResponse(content=html_content, status_code=200)

@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
    # Проверяем, не занято ли имя
    if username in manager.active_connections:
        await websocket.accept()
        await websocket.send_json({"type": "error", "message": "Имя уже занято"})
        await websocket.close()
        return
    
    await manager.connect(websocket, username)
    
    try:
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            
            msg_type = message_data.get("type", "message")
            to_user = message_data.get("to_user", None)
            text = message_data.get("text", "")
            is_image = message_data.get("is_image", False)
            image_data = message_data.get("image_data", None)
            
            if msg_type == "private_message" and to_user:
                # Сохраняем в БД
                save_message(username, to_user, text, 1 if is_image else 0, image_data)
                
                # Отправляем получателю
                await manager.send_to_user(to_user, {
                    "type": "private_message",
                    "from_user": username,
                    "text": text,
                    "is_image": is_image,
                    "image_data": image_data,
                    "timestamp": datetime.now().strftime("%H:%M")
                })
                # Отправляем отправителю (подтверждение)
                await manager.send_to_user(username, {
                    "type": "private_message",
                    "from_user": username,
                    "text": text,
                    "is_image": is_image,
                    "image_data": image_data,
                    "timestamp": datetime.now().strftime("%H:%M")
                })
            else:
                # Общее сообщение (всем)
                save_message(username, "all", text, 1 if is_image else 0, image_data)
                await manager.broadcast_to_all({
                    "type": "message",
                    "username": username,
                    "text": text,
                    "is_image": is_image,
                    "image_data": image_data,
                    "time": datetime.now().strftime("%H:%M")
                }, sender=username)
            
    except WebSocketDisconnect:
        manager.disconnect(username)
        await manager.broadcast_user_list()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
