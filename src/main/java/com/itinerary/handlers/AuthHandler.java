package com.itinerary.handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import org.mindrot.jbcrypt.BCrypt;

public class AuthHandler {

    private final MongoClient mongoClient;
    private final JWTAuth jwtAuth;
    private static final String USERS_COLLECTION = "users";

    public AuthHandler(MongoClient mongoClient, JWTAuth jwtAuth) {
        this.mongoClient = mongoClient;
        this.jwtAuth = jwtAuth;
    }

    public void signup(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("username") || !body.containsKey("password")) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Username and password are required")
                            .encode());
            return;
        }

        String username = body.getString("username");
        String password = body.getString("password");

        // Check if user already exists
        JsonObject query = new JsonObject().put("username", username);

        mongoClient.findOne(USERS_COLLECTION, query, null, result -> {
            if (result.succeeded()) {
                if (result.result() != null) {
                    // User already exists
                    ctx.response()
                            .setStatusCode(409)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Username already exists")
                                    .encode());
                } else {
                    // Create new user
                    String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                    JsonObject newUser = new JsonObject()
                            .put("username", username)
                            .put("password", hashedPassword)
                            .put("createdAt", System.currentTimeMillis());

                    mongoClient.insert(USERS_COLLECTION, newUser, insertResult -> {
                        if (insertResult.succeeded()) {
                            ctx.response()
                                    .setStatusCode(201)
                                    .putHeader("content-type", "application/json")
                                    .end(new JsonObject()
                                            .put("message", "User created successfully")
                                            .put("userId", insertResult.result())
                                            .encode());
                        } else {
                            ctx.response()
                                    .setStatusCode(500)
                                    .putHeader("content-type", "application/json")
                                    .end(new JsonObject()
                                            .put("error", "Failed to create user")
                                            .encode());
                        }
                    });
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Database error")
                                .encode());
            }
        });
    }

    public void login(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("username") || !body.containsKey("password")) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Username and password are required")
                            .encode());
            return;
        }

        String username = body.getString("username");
        String password = body.getString("password");

        // Find user in database
        JsonObject query = new JsonObject().put("username", username);

        mongoClient.findOne(USERS_COLLECTION, query, null, result -> {
            if (result.succeeded()) {
                JsonObject user = result.result();

                if (user != null && BCrypt.checkpw(password, user.getString("password"))) {
                    // Password matches, generate JWT token
                    JsonObject claims = new JsonObject()
                            .put("sub", user.getString("_id"))
                            .put("username", username)
                            .put("iat", System.currentTimeMillis() / 1000)
                            .put("exp", (System.currentTimeMillis() / 1000) + 3600); // 1 hour expiry

                    String token = jwtAuth.generateToken(claims);

                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("token", token)
                                    .put("userId", user.getString("_id"))
                                    .put("username", username)
                                    .put("message", "Login successful")
                                    .encode());
                } else {
                    // Invalid credentials
                    ctx.response()
                            .setStatusCode(401)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Invalid username or password")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Database error")
                                .encode());
            }
        });
    }
}