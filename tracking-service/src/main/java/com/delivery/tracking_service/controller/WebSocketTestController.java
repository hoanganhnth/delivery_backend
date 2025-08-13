package com.delivery.tracking_service.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebSocketTestController {

    @GetMapping("/ws-test")
    @ResponseBody
    public String webSocketTest() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Shipper Location WebSocket Test</title>
                </head>
                <body>
                    <h1>Shipper Location Real-time Tracking</h1>
                    <div id="status">Disconnected</div>
                    <div>
                        <button onclick="connect()">Connect</button>
                        <button onclick="disconnect()">Disconnect</button>
                    </div>
                    <div>
                        <input type="number" id="shipperId" placeholder="Shipper ID" value="1">
                        <button onclick="subscribeShipper()">Subscribe Shipper</button>
                        <button onclick="unsubscribeShipper()">Unsubscribe Shipper</button>
                    </div>
                    <div>
                        <input type="number" id="latitude" placeholder="Latitude" value="10.762622" step="0.000001">
                        <input type="number" id="longitude" placeholder="Longitude" value="106.660172" step="0.000001">
                        <input type="number" id="radius" placeholder="Radius (km)" value="5" step="0.1">
                        <button onclick="subscribeArea()">Subscribe Area</button>
                    </div>
                    <div id="messages" style="height: 400px; overflow-y: auto; border: 1px solid #ccc; padding: 10px; margin-top: 10px;"></div>
                    
                    <script>
                        let ws = null;
                        
                        function connect() {
                            ws = new WebSocket('ws://localhost:8090/ws/shipper-locations');
                            
                            ws.onopen = function() {
                                document.getElementById('status').textContent = 'Connected';
                                addMessage('Connected to WebSocket');
                            };
                            
                            ws.onmessage = function(event) {
                                const data = JSON.parse(event.data);
                                addMessage('Received: ' + JSON.stringify(data, null, 2));
                            };
                            
                            ws.onclose = function() {
                                document.getElementById('status').textContent = 'Disconnected';
                                addMessage('Disconnected from WebSocket');
                            };
                            
                            ws.onerror = function(error) {
                                addMessage('Error: ' + error);
                            };
                        }
                        
                        function disconnect() {
                            if (ws) {
                                ws.close();
                            }
                        }
                        
                        function subscribeShipper() {
                            const shipperId = document.getElementById('shipperId').value;
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({
                                    action: 'subscribe_shipper',
                                    shipperId: parseInt(shipperId)
                                }));
                            }
                        }
                        
                        function unsubscribeShipper() {
                            const shipperId = document.getElementById('shipperId').value;
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({
                                    action: 'unsubscribe_shipper',
                                    shipperId: parseInt(shipperId)
                                }));
                            }
                        }
                        
                        function subscribeArea() {
                            const latitude = parseFloat(document.getElementById('latitude').value);
                            const longitude = parseFloat(document.getElementById('longitude').value);
                            const radius = parseFloat(document.getElementById('radius').value);
                            
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                ws.send(JSON.stringify({
                                    action: 'subscribe_area',
                                    latitude: latitude,
                                    longitude: longitude,
                                    radius: radius
                                }));
                            }
                        }
                        
                        function addMessage(message) {
                            const messagesDiv = document.getElementById('messages');
                            const timestamp = new Date().toLocaleTimeString();
                            messagesDiv.innerHTML += '<div><strong>' + timestamp + ':</strong> ' + message + '</div>';
                            messagesDiv.scrollTop = messagesDiv.scrollHeight;
                        }
                    </script>
                </body>
                </html>
                """;
    }
}
