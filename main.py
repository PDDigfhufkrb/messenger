import asyncio
import json
import os
from datetime import datetime
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
import aiofiles

app = FastAPI()

# Хранилище активных соединений
class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []
        self.usernames: dict[WebSocket, str] = {}

    async def connect(self, websocket: WebSocket, username: str):
        await websocket.accept()
        self.active_connections.append(websocket)
        self.usernames[websocket] = username
        await self.broadcast_user_list()

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
        if websocket in self.usernames:
            del self.usernames[websocket]

    async def broadcast_user_list(self):
        user_list = list(self.usernames.values())
        for connection in self.active_connections:
            try:
                await connection.send_json({
                    "type": "user_list",
                    "users": user_list
                })
            except:
                pass

    async def send_personal_message(self, message: str, websocket: WebSocket):
        await websocket.send_text(message)

    async def broadcast(self, message: dict, sender: WebSocket = None):
        for connection in self.active_connections:
            if connection != sender:
                try:
                    await connection.send_json(message)
                except:
                    pass

manager = ConnectionManager()

# Хранилище сообщений (последние 100)
messages_history = []

@app.get("/")
async def get():
    async with aiofiles.open("index.html", encoding="utf-8") as f:
        html_content = await f.read()
    return HTMLResponse(content=html_content, status_code=200)

@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
    await manager.connect(websocket, username)
    
    # Отправляем историю новому пользователю
    for msg in messages_history[-100:]:
        await websocket.send_json(msg)
    
    try:
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            
            # Сохраняем сообщение
            msg_obj = {
                "type": "message",
                "username": username,
                "text": message_data.get("text", ""),
                "time": datetime.now().strftime("%H:%M"),
                "is_image": message_data.get("is_image", False),
                "image_data": message_data.get("image_data", None)
            }
            messages_history.append(msg_obj)
            
            # Рассылаем всем
            await manager.broadcast(msg_obj, sender=websocket)
            
    except WebSocketDisconnect:
        manager.disconnect(websocket)
        await manager.broadcast_user_list()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)