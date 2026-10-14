# AI Agent Frontend

Vue3 + Axios 前端，对接 Spring Boot AI Agent 后端。

## 功能

- **主页**：切换 AI 恋爱大师 / AI 超级智能体
- **AI 恋爱大师**：聊天室 UI，进入自动生成 `chatId`，SSE 调用 `/ai/love_app/chat/sse`
- **AI 超级智能体**：聊天室 UI，SSE 调用 `/ai/manus/chat`

## 启动

```bash
npm install
npm run dev
```

后端需运行在 `http://localhost:8123`，接口前缀 `/api`。

## 接口

| 应用 | 方法 | 路径 | 参数 |
|------|------|------|------|
| 恋爱大师 | GET (SSE) | `/api/ai/love_app/chat/sse` | `message`, `chatId` |
| 超级智能体 | GET (SSE) | `/api/ai/manus/chat` | `message` |
