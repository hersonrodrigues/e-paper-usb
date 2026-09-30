@echo off
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Please install Node.js 20 or newer, then run this file again.
  pause
  exit /b 1
)
echo Open http://localhost:5173 in Chrome or Edge after the server starts.
echo Keep this window open while using E-paper Studio.
node server.mjs
pause
