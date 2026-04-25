# Microservices API Documentation

This document provides a comprehensive overview of all APIs available in the `backend_delivery` system, organized by service.

## 1. Auth Service (Port 8081)
Base Path: `/api/auth`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/register` | POST | Public | Register a new account |
| `/login` | POST | Public | User login |
| `/refresh-token` | POST | Public | Refresh JWT token |
| `/logout` | POST | Public | Logout and invalidate token |
| `/sessions` | GET | USER/ADMIN | Get active sessions for current user |
| `/accounts/{id}` | GET | USER/ADMIN | Get account details by ID |
| `/accounts/email/{email}` | GET | Internal | Get account details by email (Internal use) |
| `/admin/accounts` | GET | ADMIN | Get all accounts |
| `/admin/accounts/{id}/block` | POST | ADMIN | Block an account |
| `/admin/accounts/{id}/unblock` | POST | ADMIN | Unblock an account |

---

## 2. User Service (Port 8082)
Base Paths: `/api/users`, `/api/addresses`

### Users API (`/api/users`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | USER | Create user profile |
| `/` | GET | USER | Get current user profile |
| `/by-auth/{authId}` | GET | Internal | Get user profile by Auth account ID |
| `/{id}` | PUT | USER | Update user profile |
| `/{id}` | DELETE | USER/ADMIN | Delete user profile |
| `/admin/statistics` | GET | ADMIN | Get user statistics |
| `/admin/all` | GET | ADMIN | List all users |
| `/admin/{userId}/block` | POST | ADMIN | Block a user |
| `/admin/{userId}/unblock` | POST | ADMIN | Unblock a user |

### Addresses API (`/api/addresses`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/users/{userId}/addresses` | GET | USER | Get all addresses for a user |
| `/{id}` | GET | USER | Get address details by ID |
| `/users/{userId}/addresses` | POST | USER | Create a new address |
| `/{id}` | PUT | USER | Update an address |
| `/{id}` | DELETE | USER | Delete an address |
| `/{id}/default` | PATCH | USER | Set address as default |

---

## 3. Restaurant Service (Port 8083)
Base Paths: `/api/restaurants`, `/api/menu-items`

### Restaurants API (`/api/restaurants`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | PARTNER/ADMIN | Create a restaurant |
| `/{id}` | PUT | PARTNER/ADMIN | Update restaurant details |
| `/{id}` | DELETE | PARTNER/ADMIN | Delete a restaurant |
| `/{id}` | GET | Public | Get restaurant details by ID |
| `/` | GET | Public | List all restaurants |
| `/search` | GET | Public | Search restaurants by keyword |
| `/creator/{creatorId}` | GET | Internal | Get restaurants by creator ID |
| `/my-restaurants` | GET | PARTNER | Get restaurants owned by current user |

### Menu Items API (`/api/menu-items`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | PARTNER/ADMIN | Create a menu item |
| `/{id}` | PUT | PARTNER/ADMIN | Update menu item details |
| `/{id}` | DELETE | PARTNER/ADMIN | Delete a menu item |
| `/restaurant/{restaurantId}` | GET | Public | Get all items for a restaurant |
| `/restaurant/{restaurantId}/available` | GET | Public | Get available items for a restaurant |
| `/my-menu-items` | GET | PARTNER | Get menu items created by current user |

---

## 4. Order Service (Port 8084)
Base Path: `/api/orders`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | USER | Place a new order |
| `/{id}` | GET | USER/PARTNER/SHIPPER | Get order details |
| `/{id}` | PUT | USER/PARTNER | Update order details |
| `/{id}` | DELETE | USER/ADMIN | Delete/Cancel order |
| `/user/{userId}` | GET | USER/ADMIN | List orders for a specific user |
| `/my-orders` | GET | USER | List current user's orders |
| `/restaurant/{restaurantId}` | GET | PARTNER/ADMIN | List orders for a restaurant |
| `/my-restaurant-orders` | GET | PARTNER | List orders for current user's restaurants |
| `/shipper/{shipperId}` | GET | SHIPPER/ADMIN | List orders assigned to a shipper |
| `/status/{status}` | GET | ADMIN | Filter orders by status |
| `/all` | GET | ADMIN | List all orders |
| `/{id}/status` | PUT | PARTNER/SHIPPER/ADMIN | Update order status |
| `/{orderId}/assign-shipper/{shipperId}` | PUT | ADMIN/Internal | Assign a shipper to an order |
| `/{id}/cancel` | PUT | USER/ADMIN | Cancel an order |

---

## 5. Delivery Service (Port 8085)
Base Path: `/api/deliveries`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/assign` | POST | Internal/ADMIN | Assign a delivery to a shipper |
| `/accept` | POST | SHIPPER | Shipper accepts a delivery task |
| `/{id}` | GET | USER/SHIPPER/ADMIN | Get delivery details |
| `/{id}/status` | PUT | SHIPPER/ADMIN | Update delivery status |
| `/shipper/{shipperId}` | GET | SHIPPER/ADMIN | Get deliveries for a shipper |
| `/shipper/{shipperId}/active` | GET | SHIPPER | Get active deliveries for a shipper |
| `/order/{orderId}` | GET | Internal | Get delivery info by order ID |

---

## 6. Shipper Service (Port 8086)
Base Path: `/api/shippers`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | Public/ADMIN | Register as a shipper |
| `/my-profile` | GET | SHIPPER | Get current shipper profile |
| `/` | PUT | SHIPPER | Update shipper profile |
| `/` | DELETE | SHIPPER/ADMIN | Delete shipper profile |
| `/online-status` | PATCH | SHIPPER | Change online/offline status |
| `/{id}` | GET | ADMIN/Internal | Get shipper profile by ID |
| `/` | GET | ADMIN | List all shippers |
| `/online` | GET | ADMIN | List online shippers |

---

## 7. Notification Service (Port 8087)
Base Path: `/api/notifications`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/send` | POST | Internal | Send a notification |
| `/user/{userId}` | GET | USER | Get notification history for a user |
| `/unread` | GET | USER | List unread notifications |
| `/unread-count` | GET | USER | Get count of unread notifications |
| `/{id}/mark-as-read` | PUT | USER | Mark a notification as read |
| `/mark-all-as-read` | PUT | USER | Mark all notifications as read |
| `/{id}` | GET | USER | Get notification details |
| `/{id}` | DELETE | USER | Delete a notification |

---

## 8. Tracking Service (Port 8090)
Base Path: `/api/shipper-locations`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/update` | POST | SHIPPER | Update current location (Redis GEO) |
| `/{shipperId}` | GET | Public | Get current location of a shipper |
| `/online` | GET | ADMIN | Get all online shippers' locations |
| `/nearby` | GET | Internal/ADMIN | Find shippers within a radius (Redis GEO) |
| `/distance` | GET | Internal/ADMIN | Calculate distance between two shippers |
| `/offline` | POST | SHIPPER | Mark shipper as offline |

---

## 9. Match Service (Port 8091)
Base Path: `/api/match`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/nearby-shippers` | POST | Internal/ADMIN | Find available shippers for an order (Non-blocking) |

---

## 10. Livestream Service (Port 8092)
Base Path: `/api/livestreams`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/` | POST | PARTNER | Create a livestream session |
| `/{id}/start` | POST | PARTNER | Start streaming (Get Agora token) |
| `/{id}/join` | POST | Public | Join a livestream as a viewer |
| `/{id}/end` | POST | PARTNER | End livestream session |
| `/active` | GET | Public | List all active livestreams |
| `/{id}` | GET | Public | Get livestream details |
| `/seller/{sellerId}` | GET | Public | List livestreams by seller |
| `/restaurant/{restaurantId}` | GET | Public | List livestreams by restaurant |

---

## 11. Settlement Service (Port 8095)
Base Path: `/api/settlement`

### Balances (`/api/settlement/balances`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/restaurant/{entityId}` | GET | PARTNER | Get restaurant current balance |
| `/shipper/{entityId}` | GET | SHIPPER | Get shipper current balance |
| `/restaurant/{entityId}/earnings` | GET | PARTNER | Get total earnings for restaurant |
| `/shipper/{entityId}/earnings` | GET | SHIPPER | Get total earnings for shipper |
| `/restaurant/{entityId}/withdraw` | POST | PARTNER | Request withdrawal for restaurant |
| `/shipper/{entityId}/withdraw` | POST | SHIPPER | Request withdrawal for shipper |
| `/shipper/{entityId}/hold` | POST | ADMIN | Hold amount in shipper balance |
| `/shipper/{entityId}/release` | POST | ADMIN | Release held amount |

### Transactions (`/api/settlement/transactions`)
| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/restaurant/{entityId}` | GET | PARTNER | Get transaction history for restaurant |
| `/shipper/{entityId}` | GET | SHIPPER | Get transaction history for shipper |
| `/{id}` | GET | PARTNER/SHIPPER | Get transaction details by ID |

---

## 12. Saga Orchestrator (Port 8089)
Base Path: `/api`

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/register` | POST | Public | Multi-step user registration (Saga) |
| `/create-order` | POST | USER | Multi-step order creation (Saga) |

---

## 13. Search Service (Port 8088)
*Work in progress - Currently only infrastructure exists.*
