@echo off
chcp 65001 >nul
echo =====================================================================
echo   Mentor Hub — Запуск полного стека (PostgreSQL + Бот + Веб-панель)
echo =====================================================================
echo.
if not exist .env (
    echo [ERROR] .env not found. Copy .env.example to .env and set a strong database password and admin keys.
    pause
    exit /b 1
)
docker compose up -d
echo.
echo [OK] Стек запущен!
echo   - Веб-панель: http://localhost:5173
echo   - REST API health: http://localhost:8080/actuator/health
echo   - Telegram-бот: включается только при TELEGRAM_BOT_ENABLED=true и валидной конфигурации
echo.
pause
