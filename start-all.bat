@echo off
chcp 65001 >nul
echo =====================================================================
echo   Mentor Hub — Запуск полного стека (PostgreSQL + Бот + Веб-панель)
echo =====================================================================
echo.
docker compose up -d
echo.
echo [OK] Стек запущен!
echo   - Веб-панель: http://localhost:5173
echo   - REST API бэкенда: http://localhost:8080/api/system/health
echo   - Telegram-бот: слушает обновления (Long Polling)
echo.
pause
