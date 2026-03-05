@echo off
echo ========================================
echo PayRoute Application Startup
echo ========================================
echo.

REM Check if Docker is running
docker --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not installed or not running
    echo Please install Docker Desktop and start it
    pause
    exit /b 1
)

echo [1/3] Stopping any existing containers...
docker-compose down

echo.
echo [2/3] Building and starting all services...
echo This may take a few minutes on first run...
docker-compose up --build -d

echo.
echo [3/3] Waiting for services to be ready...
timeout /t 10 /nobreak >nul

echo.
echo ========================================
echo PayRoute is now running!
echo ========================================
echo.
echo Backend API:  http://localhost:8080
echo Frontend UI:  http://localhost:5173
echo Swagger UI:   http://localhost:8080/swagger-ui.html
echo.
echo To view logs:
echo   docker-compose logs -f
echo.
echo To stop:
echo   docker-compose down
echo.
echo Check TESTING_GUIDE.md for API testing instructions
echo ========================================
pause
