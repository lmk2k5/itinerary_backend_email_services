# Travel Itinerary Planner API - Documentation (Updated)

## Setup Instructions

### 1. Prerequisites

* Java 21+
* Maven 3.6+
* MongoDB installed and running on `localhost:27017`
* IntelliJ IDEA (recommended)

### 2. Project Structure

```
itinerary-planner/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── travel/
│                   └── itinerary/
│                       ├── App.java
│                       ├── MainVerticle.java
│                       └── handlers/
│                           ├── AuthHandler.java
│                           ├── TripHandler.java
│                           └── WebHandler.java
├── static/
│   └── html/
│       ├── login.html
│       ├── signup.html
│       ├── dashboard.html
│       └── test.html
```

### 3. Run the Application in IntelliJ

1. Open the project in IntelliJ
2. Open `MainVerticle.java`
3. Click the green Run button
4. Ensure MongoDB is running before launching

Alternatively via terminal:

```bash
mvn clean install
mvn exec:java -Dexec.mainClass="com.travel.itinerary.MainVerticle"
```

---

## API Endpoints

### 1. Health Check

**GET** `/health`

* Verifies the server is up

---

### 2. User Authentication

#### Signup

**POST** `/signup`

```json
{
  "email": "user@example.com",
  "password": "yourpassword"
}
```

* Creates a new user

#### Login

**POST** `/login`

```json
{
  "email": "user@example.com",
  "password": "yourpassword"
}
```

* Returns JWT token and userId

---

### 3. Dashboard

#### Get All Trips for Logged-in User

**GET** `/dashboard`
**Headers:**

```
Authorization: Bearer <JWT_TOKEN>
```

* Returns all trips associated with the user

---

### 4. Trip Management

#### Create a Trip

**POST** `/trips`

```json
{
  "tripName": "Trip to Japan",
  "description": "Cherry blossom season"
}
```

**Headers:** Authorization required

#### Update Trip Info

**PUT** `/trips/{tripId}`

```json
{
  "tripName": "New Trip Name",
  "description": "Updated description"
}
```

#### Delete Trip

**DELETE** `/trips/{tripId}`

---

### 5. Day & Places Management (Per Trip)

#### Add Day

**POST** `/trips/{tripId}/days`

```json
{
  "dayNumber": 1,
  "date": "2024-08-01",
  "places": [
    {"name": "Eiffel Tower", "time": "10:00", "date": "2024-08-01"},
    {"name": "Louvre Museum", "time": "14:00", "date": "2024-08-01"}
  ]
}
```

#### Update Day

**PUT** `/trips/{tripId}/days/{dayNumber}`

```json
{
  "date": "2024-08-01",
  "places": [
    {"name": "Notre Dame", "time": "17:00", "date": "2024-08-01"}
  ]
}
```

#### Delete Day

**DELETE** `/trips/{tripId}/days/{dayNumber}`

---

## MongoDB Collections

### Users

```json
{
  "_id": "ObjectId",
  "email": "user@example.com",
  "password": "$2a$10$hashed...",
  "createdAt": 1723423423
}
```

### Trips

```json
{
  "_id": "ObjectId",
  "userId": "user_id_here",
  "tripName": "Trip to Japan",
  "description": "going to japan",
  "days": [
    {
      "dayNumber": 1,
      "date": "2024-08-01",
      "places": [
        {"name": "Tokyo Tower", "time": "10:00", "date": "2024-08-01"}
      ]
    }
  ],
  "createdAt": 1723423423,
  "updatedAt": 1723423423
}
```

---

## JWT Token

* **Required** in headers for all protected routes

```bash
Authorization: Bearer <your_token>
```

* Tokens expire in **1 hour**

---

## Testing Tools

### Postman

* Import the above endpoints manually or use example requests
* Store `jwt_token` in environment

### Curl

Example Flow:

```bash
# Signup
curl -X POST http://localhost:8888/signup -H "Content-Type: application/json" -d '{"email": "john@example.com", "password": "pass123"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8888/login -H "Content-Type: application/json" -d '{"email": "john@example.com", "password": "pass123"}' | jq -r '.token')

# Create Trip
TRIP_ID=$(curl -s -X POST http://localhost:8888/trips -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"tripName": "Japan", "description": "Spring trip"}' | jq -r '.trip._id')

# Add Day
curl -X POST http://localhost:8888/trips/$TRIP_ID/days -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"dayNumber": 1, "date": "2024-04-01", "places": [{"name": "Tokyo Tower", "time": "10:00", "date": "2024-04-01"}]}'
```

---

## Static HTML Routes (Optional UI Pages)

* `/auth/login` → `static/html/login.html`
* `/auth/signup` → `static/html/signup.html`
* `/dashboard` → `static/html/dashboard.html`
* `/test` → `static/html/test.html`

---

## Final Notes

* Fully integrated with MongoDB
* Passwords hashed with BCrypt
* JWT-based authentication secured
* Trip data and days tied directly to the authenticated user
* All CRUD operations functional

<mark>Please plug Frontend in all backend endpoints are ready and usable</mark>