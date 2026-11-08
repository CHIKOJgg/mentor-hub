@echo off
chcp 65001 >nul
echo =====================================================================
echo   Mentor Hub — Запуск Telegram-бота @nastav_beauty_bot
echo =====================================================================
echo.

if "%TELEGRAM_BOT_TOKEN%"=="" set TELEGRAM_BOT_TOKEN=9082484274:AAGIHVmoY5T3xfX-cbF3h37BOhSHckKq4_w
if "%TELEGRAM_BOT_USERNAME%"=="" set TELEGRAM_BOT_USERNAME=nastav_beauty_bot

echo [INFO] Подключение к Telegram боту @%TELEGRAM_BOT_USERNAME%...

if not exist target\mentor-tracker-backend-1.0.0.jar (
    echo [INFO] Сборка артефакта...
    call mvn clean package -DskipTests
)

echo [INFO] Запуск Spring Boot сервера с ботом на порту 8080...
java -Dtelegram.bot.token=%TELEGRAM_BOT_TOKEN% -Dtelegram.bot.username=%TELEGRAM_BOT_USERNAME% -jar target\mentor-tracker-backend-1.0.0.jar
pause
