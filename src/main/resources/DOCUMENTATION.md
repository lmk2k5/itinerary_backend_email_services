Setup Instructions
1. Prerequisites
   To set up and run the application, you will need the following installed on your system:

Java 21+

Maven 3.6+

MongoDB installed and running on localhost:27017 (or a different MongoDB server you configure)

IntelliJ IDEA or any other Java IDE (recommended for ease of use)

2. Project Structure
   Here is an overview of the project structure:

graphql
Copy
Edit
itinerary-planner/
├── pom.xml                      # Maven dependencies
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── travel/
│                   └── itinerary/
│                       ├── App.java
│                       ├── MainVerticle.java      # Main class for starting the Vert.x HTTP server
│                       └── handlers/
│                           ├── AuthHandler.java    # Handles user authentication routes
│                           ├── TripHandler.java    # Handles trip-related API routes
│                           └── WebHandler.java     # Serves static HTML pages for frontend
├── static/
│   └── html/
│       ├── login.html            # Login page (for frontend)
│       ├── signup.html           # Signup page (for frontend)
│       ├── dashboard.html        # Dashboard page (for frontend)
│       └── test.html             # Test page (optional for testing)
3. Running the Application
   To run the application:

Open the project in IntelliJ IDEA.

Open MainVerticle.java in the editor.

Click the green Run button in IntelliJ to run the application.

Ensure MongoDB is running before launching the application.

Alternatively, you can run the application from the terminal:

bash
Copy
Edit
mvn clean install
mvn exec:java -Dexec.mainClass="com.travel.itinerary.MainVerticle"
API Endpoints
1. Health Check
   GET /health

Description: Verifies if the server is up and running.

Response:

json
Copy
Edit
{
"status": "UP"
}
2. User Authentication
   Signup
   POST /signup

Description: Creates a new user in the system.

Request Body:

json
Copy
Edit
{
"email": "user@example.com",
"password": "yourpassword"
}
Response:

json
Copy
Edit
{
"message": "User created successfully"
}
Login
POST /login

Description: Logs the user in and returns a JWT token for authentication.

Request Body:

json
Copy
Edit
{
"email": "user@example.com",
"password": "yourpassword"
}
Response:

json
Copy
Edit
{
"token": "<JWT_TOKEN>",
"userId": "<USER_ID>"
}
3. Dashboard
   Get All Trips for Logged-in User
   GET /dashboard

Headers:

Authorization: Bearer <JWT_TOKEN>

Description: Retrieves all trips associated with the logged-in user.

Response:

json
Copy
Edit
{
"trips": [
{
"tripName": "Trip to Japan",
"description": "Cherry blossom season",
"days": [
{
"dayNumber": 1,
"date": "2024-08-01",
"places": [
{"name": "Eiffel Tower", "time": "10:00", "date": "2024-08-01"},
{"name": "Louvre Museum", "time": "14:00", "date": "2024-08-01"}
]
}
]
}
]
}
4. Trip Management
   Create a Trip
   POST /trips

Headers:

Authorization: Bearer <JWT_TOKEN>

Description: Creates a new trip for the logged-in user.

Request Body:

json
Copy
Edit
{
"tripName": "Trip to Japan",
"description": "Cherry blossom season"
}
Response:

json
Copy
Edit
{
"message": "Trip created successfully",
"trip": {
"tripName": "Trip to Japan",
"description": "Cherry blossom season",
"days": []
}
}
Update Trip Info
PUT /trips/{tripId}

Description: Updates an existing trip's information (e.g., trip name or description).

Request Body:

json
Copy
Edit
{
"tripName": "Updated Trip Name",
"description": "Updated description"
}
Response:

json
Copy
Edit
{
"message": "Trip updated successfully"
}
Delete Trip
DELETE /trips/{tripId}

Description: Deletes an existing trip.

Response:

json
Copy
Edit
{
"message": "Trip deleted successfully"
}
5. Day & Places Management (Per Trip)
   Add Day
   POST /trips/{tripId}/days

Description: Adds a new day to a trip.

Request Body:

json
Copy
Edit
{
"dayNumber": 1,
"date": "2024-08-01",
"places": [
{"name": "Eiffel Tower", "time": "10:00", "date": "2024-08-01"},
{"name": "Louvre Museum", "time": "14:00", "date": "2024-08-01"}
]
}
Response:

json
Copy
Edit
{
"message": "Day added successfully"
}
Update Day
PUT /trips/{tripId}/days/{dayNumber}

Description: Updates the details of an existing day (places and activities).

Request Body:

json
Copy
Edit
{
"date": "2024-08-01",
"places": [
{"name": "Notre Dame", "time": "17:00", "date": "2024-08-01"}
]
}
Response:

json
Copy
Edit
{
"message": "Day updated successfully"
}
Delete Day
DELETE /trips/{tripId}/days/{dayNumber}

Description: Deletes a specific day from a trip.

Response:

json
Copy
Edit
{
"message": "Day deleted successfully"
}
MongoDB Collections
Users
json
Copy
Edit
{
"_id": "ObjectId",
"email": "user@example.com",
"password": "$2a$10$hashed...",
"createdAt": 1723423423
}
Trips
json
Copy
Edit
{
"_id": "ObjectId",
"userId": "user_id_here",
"tripName": "Trip to Japan",
"description": "going to Japan",
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
JWT Token
Required in the headers for all protected routes.

bash
Copy
Edit
Authorization: Bearer <your_token>
Token Expiry: Tokens expire in 1 hour.

Testing Tools
Postman
Import the above endpoints manually or use example requests.

Store the jwt_token in the environment for easy access.

cURL Example Flow:
bash
Copy
Edit
# Signup
curl -X POST http://localhost:8888/signup -H "Content-Type: application/json" -d '{"email": "john@example.com", "password": "pass123"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8888/login -H "Content-Type: application/json" -d '{"email": "john@example.com", "password": "pass123"}' | jq -r '.token')

# Create Trip
TRIP_ID=$(curl -s -X POST http://localhost:8888/trips -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"tripName": "Japan", "description": "Spring trip"}' | jq -r '.trip._id')

# Add Day
curl -X POST http://localhost:8888/trips/$TRIP_ID/days -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"dayNumber": 1, "date": "2024-04-01", "places": [{"name": "Tokyo Tower", "time": "10:00", "date": "2024-04-01"}]}'
Static HTML Routes (Optional UI Pages)
The following routes serve static HTML pages, which can be used for testing or frontend integration:

/auth/login → static/html/login.html

/auth/signup → static/html/signup.html

/dashboard → static/html/dashboard.html

/test → static/html/test.html

Final Notes
Fully integrated with MongoDB.

Passwords are securely hashed using BCrypt.

JWT-based authentication is used for securing routes.

Trip data and days are directly tied to the authenticated user.

All CRUD operations for trips and days are functional.

