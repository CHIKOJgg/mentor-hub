@echo off
chcp 65001 >nul
echo =====================================================================
echo   Mentor Hub — Запуск Telegram-бота @nastav_beauty_bot
echo =====================================================================
echo.

if exist .env (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
        if "%%A"=="TELEGRAM_BOT_TOKEN" set TELEGRAM_BOT_TOKEN=%%B
        if "%%A"=="TELEGRAM_BOT_USERNAME" set TELEGRAM_BOT_USERNAME=%%B
        if "%%A"=="DB_HOST" set DB_HOST=%%B
        if "%%A"=="DB_PORT" set DB_PORT=%%B
        if "%%A"=="DB_NAME" set DB_NAME=%%B
        if "%%A"=="DB_USER" set DB_USER=%%B
        if "%%A"=="DB_PASSWORD" set DB_PASSWORD=%%B
        if "%%A"=="AI_API_KEY" set AI_API_KEY=%%B
        if "%%A"=="ADMIN_MENTOR_TOKEN" set ADMIN_MENTOR_TOKEN=%%B
        if "%%A"=="ADMIN_CURATOR_TOKEN" set ADMIN_CURATOR_TOKEN=%%B
        if "%%A"=="ADMIN_MENTOR_TELEGRAM_USER_ID" set ADMIN_MENTOR_TELEGRAM_USER_ID=%%B
        if "%%A"=="ADMIN_CURATOR_TELEGRAM_USER_ID" set ADMIN_CURATOR_TELEGRAM_USER_ID=%%B
    )
)
if "%TELEGRAM_BOT_TOKEN%"=="" (
    echo [ERROR] TELEGRAM_BOT_TOKEN is not set.
    echo Please set it in your environment or create a .env file based on .env.example.
    pause
    exit /b 1
)
if "%DB_PASSWORD%"=="" (
    echo [ERROR] DB_PASSWORD is not set. Configure it in your environment or .env file.
    pause
    exit /b 1
)
if "%ADMIN_MENTOR_TOKEN%"=="" (
    echo [ERROR] ADMIN_MENTOR_TOKEN is not set.
    pause
    exit /b 1
)
if "%ADMIN_CURATOR_TOKEN%"=="" (
    echo [ERROR] ADMIN_CURATOR_TOKEN is not set.
    pause
    exit /b 1
)
if "%ADMIN_MENTOR_TELEGRAM_USER_ID%"=="" (
    echo [ERROR] ADMIN_MENTOR_TELEGRAM_USER_ID is not set.
    pause
    exit /b 1
)
if "%ADMIN_CURATOR_TELEGRAM_USER_ID%"=="" (
    echo [ERROR] ADMIN_CURATOR_TELEGRAM_USER_ID is not set.
    pause
    exit /b 1
)
if "%TELEGRAM_BOT_USERNAME%"=="" set TELEGRAM_BOT_USERNAME=MentorHubBot
set SPRING_PROFILES_ACTIVE=production
set TELEGRAM_BOT_ENABLED=true
if "%TELEGRAM_BOT_MODE%"=="" set TELEGRAM_BOT_MODE=long-polling

echo [INFO] Подключение к Telegram боту @%TELEGRAM_BOT_USERNAME%...

if not exist target\mentor-tracker-backend-1.0.0.jar (
    echo [INFO] Сборка артефакта...
    call mvn -B clean verify
)

echo [INFO] Запуск Spring Boot сервера с ботом на порту 8080...
java -jar target\mentor-tracker-backend-1.0.0.jar
pause
