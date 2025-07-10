# Travel Itinerary Planner API - Testing Guide

## Setup Instructions

### 1. Prerequisites
- Java 21+ installed
- Maven 3.6+ installed
- NOTE: <u>Intellij does all this except mongodb INSTALL THAT</u>
- MongoDB running on localhost:27017

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
│                           └── TripHandler.java
```

### 3. Run the Application
```bash
# Clone/create the project structure above
# Navigate to project directory
cd itinerary-planner

# Install dependencies
mvn clean install

# Run the application
mvn exec:java -Dexec.mainClass="com.travel.itinerary.App"
```

The server will start on port 8888.

## API Endpoints Testing

### 1. Health Check
```bash
curl -X GET http://localhost:8888/health
```

### 2. User Registration (Signup)
```bash
curl -X POST http://localhost:8888/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

### 3. User Login
```bash
curl -X POST http://localhost:8888/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

**Save the JWT token from the response - you'll need it for protected routes!**

### 4. Get Dashboard (All Trips)
```bash
curl -X GET http://localhost:8888/dashboard \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

### 5. Create a New Trip
```bash
curl -X POST http://localhost:8888/trips \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -d '{
    "tripName": "My Europe Trip",
    "description": "2 weeks in Europe"
  }'
```

### 6. Add a Day to Trip
```bash
curl -X POST http://localhost:8888/trips/TRIP_ID_HERE/days \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -d '{
    "dayNumber": 1,
    "date": "2024-08-01",
    "places": [
      {
        "name": "Eiffel Tower",
        "time": "10:00",
        "date": "2024-08-01"
      },
      {
        "name": "Louvre Museum",
        "time": "14:00",
        "date": "2024-08-01"
      }
    ]
  }'
```

### 7. Update a Day
```bash
curl -X PUT http://localhost:8888/trips/TRIP_ID_HERE/days/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -d '{
    "date": "2024-08-01",
    "places": [
      {
        "name": "Eiffel Tower",
        "time": "09:00",
        "date": "2024-08-01"
      },
      {
        "name": "Louvre Museum",
        "time": "14:00",
        "date": "2024-08-01"
      },
      {
        "name": "Notre Dame",
        "time": "17:00",
        "date": "2024-08-01"
      }
    ]
  }'
```

### 8. Update Trip Details
```bash
curl -X PUT http://localhost:8888/trips/TRIP_ID_HERE \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -d '{
    "tripName": "My Amazing Europe Trip",
    "description": "3 weeks exploring Europe"
  }'
```

### 9. Delete a Day
```bash
curl -X DELETE http://localhost:8888/trips/TRIP_ID_HERE/days/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

### 10. Delete a Trip
```bash
curl -X DELETE http://localhost:8888/trips/TRIP_ID_HERE \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

## MongoDB Data Structure

### Users Collection
```json
{
  "_id": "ObjectId",
  "username": "testuser",
  "password": "$2a$10$hashedpassword...",
  "createdAt": 1234567890
}
```

### Trips Collection
```json
{
  "_id": "ObjectId",
  "userId": "user_id_here",
  "tripName": "My Europe Trip",
  "description": "2 weeks in Europe",
  "days": [
    {
      "dayNumber": 1,
      "date": "2024-08-01",
      "places": [
        {
          "name": "Eiffel Tower",
          "time": "10:00",
          "date": "2024-08-01"
        },
        {
          "name": "Louvre Museum",
          "time": "14:00",
          "date": "2024-08-01"
        }
      ]
    },
    {
      "dayNumber": 2,
      "date": "2024-08-02",
      "places": [
        {
          "name": "Big Ben",
          "time": "11:00",
          "date": "2024-08-02"
        }
      ]
    }
  ],
  "createdAt": 1234567890,
  "updatedAt": 1234567890
}
```

## Testing with Postman

1. **Import Collection**: Create a new Postman collection
2. **Set Environment Variables**:
    - `base_url`: http://localhost:8888
    - `jwt_token`: (will be set after login)
3. **Test Flow**:
    - Register a user
    - Login and save JWT token
    - Create trip
    - Add days with places
    - Test CRUD operations

## Common Issues & Solutions

### 1. MongoDB Connection Error
- Make sure MongoDB is running: `mongod`
- Check if port 27017 is available

### 2. JWT Token Expired
- Login again to get a new token
- Tokens expire after 1 hour

### 3. CORS Issues
- The server has CORS enabled for all origins
- Make sure to include proper headers

### 4. Port Already in Use
- Change port in MainVerticle.java if 8888 is occupied

## Example Test Sequence
```bash
# 1. Register
curl -X POST http://localhost:8888/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "password": "pass123"}'

# 2. Login and get token
TOKEN=$(curl -X POST http://localhost:8888/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "password": "pass123"}' | jq -r '.token')

# 3. Create trip
TRIP_ID=$(curl -X POST http://localhost:8888/trips \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"tripName": "Japan Trip", "description": "Cherry blossom season"}' | jq -r '.trip._id')

# 4. Add day
curl -X POST http://localhost:8888/trips/$TRIP_ID/days \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "dayNumber": 1,
    "date": "2024-04-01",
    "places": [
      {"name": "Tokyo Tower", "time": "10:00", "date": "2024-04-01"},
      {"name": "Shibuya Crossing", "time": "15:00", "date": "2024-04-01"}
    ]
  }'

# 5. Get dashboard
curl -X GET http://localhost:8888/dashboard \
  -H "Authorization: Bearer $TOKEN"
```
