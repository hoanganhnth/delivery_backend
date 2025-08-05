@echo off
echo ===========================================
echo  🔔 NOTIFICATION SERVICE QUICK START 🔔
echo ===========================================
echo.

:menu
echo Chọn action:
echo [1] Build và Start tất cả services
echo [2] Start services (đã build)
echo [3] Stop tất cả services
echo [4] Rebuild notification-service
echo [5] View logs
echo [6] Clean up (remove containers + volumes)
echo [7] Setup database
echo [8] Create Kafka topics
echo [9] Exit
echo.
set /p choice="Nhập lựa chọn (1-9): "

if "%choice%"=="1" goto build_start
if "%choice%"=="2" goto start
if "%choice%"=="3" goto stop
if "%choice%"=="4" goto rebuild
if "%choice%"=="5" goto logs
if "%choice%"=="6" goto cleanup
if "%choice%"=="7" goto setup_db
if "%choice%"=="8" goto create_topics
if "%choice%"=="9" goto exit
goto menu

:build_start
echo.
echo 🔨 Building và starting tất cả services...
docker-compose down
docker-compose build
docker-compose up -d
echo.
echo ✅ Services started! Checking status...
timeout /t 5 /nobreak > nul
docker-compose ps
echo.
echo 📊 Management URLs:
echo - Notification Service: http://localhost:8087
echo - Kafka UI: http://localhost:8080
echo - Redis Commander: http://localhost:8081  
echo - pgAdmin: http://localhost:8082
echo.
goto menu

:start
echo.
echo 🚀 Starting services...
docker-compose up -d
echo.
echo ✅ Services started!
docker-compose ps
goto menu

:stop
echo.
echo 🛑 Stopping services...
docker-compose down
echo ✅ Services stopped!
goto menu

:rebuild
echo.
echo 🔄 Rebuilding notification-service...
docker-compose stop notification-service
docker-compose build notification-service
docker-compose up -d notification-service
echo ✅ Notification service rebuilt and restarted!
goto menu

:logs
echo.
echo Chọn service để xem logs:
echo [1] notification-service
echo [2] postgres
echo [3] redis
echo [4] kafka
echo [5] Tất cả services
echo [6] Back to main menu
echo.
set /p log_choice="Nhập lựa chọn (1-6): "

if "%log_choice%"=="1" docker-compose logs -f notification-service
if "%log_choice%"=="2" docker-compose logs -f postgres
if "%log_choice%"=="3" docker-compose logs -f redis
if "%log_choice%"=="4" docker-compose logs -f kafka
if "%log_choice%"=="5" docker-compose logs -f
if "%log_choice%"=="6" goto menu
goto menu

:cleanup
echo.
echo ⚠️  WARNING: Sẽ xóa tất cả containers và volumes!
set /p confirm="Bạn có chắc chắn? (y/N): "
if /i not "%confirm%"=="y" goto menu

echo 🧹 Cleaning up...
docker-compose down -v --remove-orphans
docker system prune -f
echo ✅ Cleanup completed!
goto menu

:setup_db
echo.
echo 🗃️ Setting up database...
echo Waiting for PostgreSQL to be ready...
timeout /t 10 /nobreak > nul

docker-compose exec postgres psql -U postgres -d notification_service_db -c "\dt"
if %errorlevel% equ 0 (
    echo ✅ Database is ready!
    echo Running schema setup...
    docker-compose exec postgres psql -U postgres -d notification_service_db -f /docker-entrypoint-initdb.d/schema.sql
    echo ✅ Database setup completed!
) else (
    echo ❌ Database not ready. Please check if PostgreSQL is running.
)
goto menu

:create_topics
echo.
echo 📨 Creating Kafka topics...
echo Waiting for Kafka to be ready...
timeout /t 15 /nobreak > nul

docker-compose exec kafka kafka-topics --create --topic order.created --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic order.status-updated --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic delivery.status-updated --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic delivery.shipper-assigned --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic match.shipper-found --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic match.shipper-request --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic match.shipper-accepted --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul
docker-compose exec kafka kafka-topics --create --topic match.shipper-rejected --bootstrap-server localhost:9092 --replication-factor 1 --partitions 3 2>nul

echo.
echo 📋 Listing created topics:
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

echo ✅ Kafka topics created!
goto menu

:exit
echo.
echo 👋 Goodbye!
pause
exit

:error
echo.
echo ❌ Có lỗi xảy ra. Vui lòng thử lại.
pause
goto menu
