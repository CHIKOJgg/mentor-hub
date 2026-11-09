@echo off
chcp 65001 >nul
echo =====================================================================
echo   Mentor Hub — Запуск Telegram-бота @nastav_beauty_bot
echo =====================================================================
echo.

if "%TELEGRAM_BOT_TOKEN%"=="" (
    if exist .env (
        for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
            if "%%A"=="TELEGRAM_BOT_TOKEN" set TELEGRAM_BOT_TOKEN=%%B
            if "%%A"=="TELEGRAM_BOT_USERNAME" set TELEGRAM_BOT_USERNAME=%%B
        )
    )
)
if "%TELEGRAM_BOT_TOKEN%"=="" (
    echo [ERROR] TELEGRAM_BOT_TOKEN is not set.
    echo Please set it in your environment or create a .env file based on .env.example.
    pause
    exit /b 1
)
if "%TELEGRAM_BOT_USERNAME%"=="" set TELEGRAM_BOT_USERNAME=MentorHubBot

echo [INFO] Подключение к Telegram боту @%TELEGRAM_BOT_USERNAME%...

if not exist target\mentor-tracker-backend-1.0.0.jar (
    echo [INFO] Сборка артефакта...
    call mvn clean package -DskipTests
)

echo [INFO] Запуск Spring Boot сервера с ботом на порту 8080...
java -Dtelegram.bot.token=%TELEGRAM_BOT_TOKEN% -Dtelegram.bot.username=%TELEGRAM_BOT_USERNAME% -jar target\mentor-tracker-backend-1.0.0.jar
pause
