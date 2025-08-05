# 🛠️ Notification Service - Setup & Testing Guide

## 🚀 **Quick Start Setup**

### 1. Database Setup
```bash
# Tạo database PostgreSQL
psql -U postgres
CREATE DATABASE notification_service_db;
\q

# Run SQL schema
psql -U postgres -d notification_service_db -f database/schema.sql
```

### 2. Redis Setup
```bash
# Install Redis (Windows)
# Download từ: https://github.com/microsoftarchive/redis/releases
# Hoặc dùng Docker:
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Start Redis server
redis-server
```

### 3. Kafka Setup
```bash
# Start Zookeeper
bin\windows\zookeeper-server-start.bat config\zookeeper.properties

# Start Kafka
bin\windows\kafka-server-start.bat config\server.properties

# Create topics
bin\windows\kafka-topics.bat --create --topic order.created --bootstrap-server localhost:9092
bin\windows\kafka-topics.bat --create --topic order.status-updated --bootstrap-server localhost:9092
bin\windows\kafka-topics.bat --create --topic delivery.status-updated --bootstrap-server localhost:9092
bin\windows\kafka-topics.bat --create --topic delivery.shipper-assigned --bootstrap-server localhost:9092
```

### 4. Firebase Setup
```bash
# 1. Tạo Firebase project tại https://console.firebase.google.com/
# 2. Enable Cloud Messaging (FCM)
# 3. Generate service account key
# 4. Download JSON file và đặt tại: src/main/resources/firebase-service-account.json
```

### 5. Build & Run
```bash
# Build project
mvn clean compile

# Run application
mvn spring-boot:run

# Check if running
curl http://localhost:8087/actuator/health
```

## 🧪 **Testing Guide**

### 1. WebSocket Testing

#### Frontend Test Client (HTML)
```html
<!DOCTYPE html>
<html>
<head>
    <title>WebSocket Test</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <div id="messages"></div>
    <button onclick="connect()">Connect</button>
    <button onclick="disconnect()">Disconnect</button>
    <button onclick="sendTestMessage()">Send Test Message</button>

    <script>
        let stompClient = null;
        const userId = 123;

        function connect() {
            const socket = new SockJS('http://localhost:8087/ws');
            stompClient = Stomp.over(socket);
            
            stompClient.connect({}, function (frame) {
                console.log('Connected: ' + frame);
                document.getElementById('messages').innerHTML += '<p>Connected to WebSocket</p>';
                
                // Subscribe to user notifications
                stompClient.subscribe('/topic/user/' + userId, function (message) {
                    const notification = JSON.parse(message.body);
                    console.log('Received notification:', notification);
                    document.getElementById('messages').innerHTML += 
                        '<p><strong>' + notification.title + '</strong>: ' + notification.message + '</p>';
                });
                
                // Subscribe to broadcast
                stompClient.subscribe('/topic/notifications', function (message) {
                    const notification = JSON.parse(message.body);
                    console.log('Received broadcast:', notification);
                    document.getElementById('messages').innerHTML += 
                        '<p><strong>BROADCAST:</strong> ' + notification.message + '</p>';
                });
            });
        }

        function disconnect() {
            if (stompClient !== null) {
                stompClient.disconnect();
            }
            console.log("Disconnected");
        }

        function sendTestMessage() {
            if (stompClient !== null) {
                stompClient.send('/app/send', {}, JSON.stringify({
                    'type': 'TYPING',
                    'userId': userId,
                    'message': 'Test typing message',
                    'timestamp': new Date().toISOString()
                }));
            }
        }
    </script>
</body>
</html>
```

#### Postman WebSocket Test
```javascript
// Postman Pre-request Script for WebSocket
const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8087/ws');

ws.on('open', function open() {
    console.log('WebSocket connected');
    ws.send(JSON.stringify({
        type: 'NOTIFICATION',
        userId: 123,
        message: 'Test from Postman'
    }));
});

ws.on('message', function incoming(data) {
    console.log('Received:', data.toString());
});
```

### 2. REST API Testing

#### Send Notification
```bash
curl -X POST http://localhost:8087/api/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-Role: ADMIN" \
  -d '{
    "userId": 123,
    "title": "Test Notification",
    "message": "This is a test notification",
    "type": "ORDER_CREATED",
    "priority": "HIGH",
    "relatedEntityId": 456,
    "relatedEntityType": "ORDER"
  }'
```

#### Get User Notifications
```bash
curl -X GET http://localhost:8087/api/notifications/user/123 \
  -H "X-User-Id: 123"
```

#### Mark as Read
```bash
curl -X PUT http://localhost:8087/api/notifications/1/read \
  -H "X-User-Id: 123"
```

#### Register FCM Token
```bash
curl -X POST http://localhost:8087/api/firebase/register-token \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 123" \
  -d '{
    "token": "your_fcm_token_here"
  }'
```

### 3. Kafka Event Testing

#### Send Order Created Event
```bash
# Terminal 1: Start Kafka console producer
bin\windows\kafka-console-producer.bat --topic order.created --bootstrap-server localhost:9092

# Send JSON event:
{
  "orderId": 123,
  "userId": 456,
  "restaurantName": "Nhà hàng ABC",
  "totalAmount": 150000,
  "status": "CREATED",
  "timestamp": "2025-01-14T10:30:00"
}
```

#### Send Delivery Status Event
```bash
# Terminal 2: Start another producer
bin\windows\kafka-console-producer.bat --topic delivery.status-updated --bootstrap-server localhost:9092

# Send delivery event:
{
  "deliveryId": 789,
  "orderId": 123,
  "userId": 456,
  "status": "PICKED_UP",
  "shipperName": "Nguyễn Văn A",
  "shipperPhone": "0901234567",
  "timestamp": "2025-01-14T11:30:00"
}
```

#### Monitor Kafka Consumer
```bash
# Check consumer groups
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --list

# Check consumer lag
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group notification-service --describe
```

### 4. Redis Testing

#### Check Redis Data
```bash
# Connect to Redis CLI
redis-cli

# Check user sessions
KEYS "user_session:*"

# Check FCM tokens
KEYS "fcm_tokens:*"

# Check notification cache
KEYS "notification:*"

# Get specific data
GET "user_session:123"
SMEMBERS "fcm_tokens:123"
```

### 5. Integration Testing

#### Full Flow Test
```javascript
// Test script để verify toàn bộ flow
async function testFullFlow() {
    const userId = 123;
    
    // 1. Connect WebSocket
    const ws = new WebSocket('ws://localhost:8087/ws');
    
    // 2. Register FCM token
    await fetch('http://localhost:8087/api/firebase/register-token', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': userId
        },
        body: JSON.stringify({
            token: 'test_fcm_token'
        })
    });
    
    // 3. Send Kafka event
    // (Use Kafka producer để send order.created event)
    
    // 4. Verify notification created
    const notifications = await fetch(`http://localhost:8087/api/notifications/user/${userId}`, {
        headers: { 'X-User-Id': userId }
    });
    
    console.log('Notifications:', await notifications.json());
    
    // 5. Check WebSocket received message
    ws.onmessage = (event) => {
        console.log('WebSocket received:', JSON.parse(event.data));
    };
}
```

## 📊 **Performance Testing**

### Load Testing with Artillery
```yaml
# artillery-test.yml
config:
  target: 'http://localhost:8087'
  phases:
    - duration: 60
      arrivalRate: 10
  variables:
    userId:
      - 123
      - 456
      - 789

scenarios:
  - name: "Notification API Load Test"
    weight: 100
    flow:
      - post:
          url: "/api/notifications/send"
          headers:
            Content-Type: "application/json"
            X-User-Id: "1"
            X-Role: "ADMIN"
          json:
            userId: "{{ userId }}"
            title: "Load Test Notification"
            message: "This is a load test notification"
            type: "ORDER_CREATED"
            priority: "MEDIUM"
      - get:
          url: "/api/notifications/user/{{ userId }}"
          headers:
            X-User-Id: "{{ userId }}"
```

```bash
# Run load test
npx artillery run artillery-test.yml
```

### WebSocket Load Testing
```javascript
// websocket-load-test.js
const WebSocket = require('ws');

async function loadTestWebSocket() {
    const connections = [];
    const numConnections = 100;
    
    for (let i = 0; i < numConnections; i++) {
        const ws = new WebSocket('ws://localhost:8087/ws');
        connections.push(ws);
        
        ws.on('open', () => {
            console.log(`Connection ${i} opened`);
            
            // Send messages periodically
            setInterval(() => {
                ws.send(JSON.stringify({
                    type: 'TYPING',
                    userId: i,
                    message: `Load test message from ${i}`
                }));
            }, 1000);
        });
        
        ws.on('message', (data) => {
            console.log(`Connection ${i} received:`, data.toString());
        });
    }
    
    // Keep connections alive
    setTimeout(() => {
        connections.forEach(ws => ws.close());
        console.log('All connections closed');
    }, 60000);
}

loadTestWebSocket();
```

## 🔍 **Troubleshooting**

### Common Issues

#### 1. WebSocket Connection Failed
```bash
# Check if WebSocket endpoint is available
curl -I http://localhost:8087/ws

# Check firewall/proxy settings
netstat -an | findstr 8087
```

#### 2. Redis Connection Issues
```bash
# Check Redis server status
redis-cli ping

# Check Redis logs
redis-cli monitor
```

#### 3. Kafka Consumer Not Receiving
```bash
# Check topic exists
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092

# Check consumer group
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group notification-service --describe

# Reset consumer offset
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group notification-service --reset-offsets --to-earliest --all-topics --execute
```

#### 4. Firebase Push Notification Failed
```java
// Check logs for Firebase errors
grep "Firebase" logs/notification-service.log

// Verify service account key
ls -la src/main/resources/firebase-service-account.json

// Test Firebase connectivity
curl -X POST https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

### Log Analysis Commands
```bash
# Real-time log monitoring
tail -f logs/notification-service.log

# Error tracking
grep -E "(ERROR|Exception|Failed)" logs/notification-service.log

# WebSocket activity
grep "WebSocket" logs/notification-service.log

# Notification delivery
grep -E "(📤|📡|📱)" logs/notification-service.log

# Performance metrics
grep -E "(took|duration|elapsed)" logs/notification-service.log
```

---

**✅ Setup Complete!** Notification Service sẵn sàng để handle real-time notifications với full testing coverage! 🎉
