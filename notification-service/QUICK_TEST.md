# 🎯 Quick Test Commands for Notification Service

# Build and run
mvn clean compile spring-boot:run

# Or with Docker
./start.bat

# Test WebSocket connection
curl -X GET http://localhost:8087/ws

# Send test notification
curl -X POST http://localhost:8087/api/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-Role: ADMIN" \
  -d '{
    "userId": 123,
    "title": "Test Notification",
    "message": "This is a test message",
    "type": "ORDER_CREATED",
    "priority": "HIGH"
  }'

# Get user notifications
curl -H "X-User-Id: 123" http://localhost:8087/api/notifications/user/123

# Register FCM token
curl -X POST http://localhost:8087/api/firebase/register-token \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 123" \
  -d '{"token": "test_fcm_token"}'

# Check Redis data
redis-cli
> KEYS "*"
> GET "user_session:123"

# Send Kafka test event
bin/kafka-console-producer.bat --topic order.created --bootstrap-server localhost:9092
{
  "orderId": 123,
  "userId": 456, 
  "restaurantName": "Test Restaurant",
  "totalAmount": 100000,
  "status": "CREATED"
}

# Health check
curl http://localhost:8087/actuator/health
