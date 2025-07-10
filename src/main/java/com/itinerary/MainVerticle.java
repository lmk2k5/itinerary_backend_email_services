package com.itinerary;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import com.itinerary.handlers.AuthHandler;
import com.itinerary.handlers.TripHandler;
import com.itinerary.handlers.WebHandler;

public class MainVerticle extends AbstractVerticle {

    private MongoClient mongoClient;
    private JWTAuth jwtAuth;
    private ThymeleafTemplateEngine templateEngine;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        // MongoDB configuration
        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "travel_planner");

        mongoClient = MongoClient.createShared(vertx, mongoConfig);

        // JWT configuration
        JWTAuthOptions jwtConfig = new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer("your-secret-key-change-this-in-production"));

        jwtAuth = JWTAuth.create(vertx, jwtConfig);

        // Template engine
        templateEngine = ThymeleafTemplateEngine.create(vertx);

        // Create HTTP server
        HttpServer server = vertx.createHttpServer();

        // Create router
        Router router = Router.router(vertx);

        // Enable CORS
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
                .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization"));

        // Enable body parsing
        router.route().handler(BodyHandler.create());

        // Static files (CSS, JS, images)
        router.route("/static/*").handler(StaticHandler.create("static"));

        // Initialize handlers
        AuthHandler authHandler = new AuthHandler(mongoClient, jwtAuth);
        TripHandler tripHandler = new TripHandler(mongoClient);
        WebHandler webHandler = new WebHandler(mongoClient, jwtAuth, templateEngine);

        // Web routes (HTML pages)
        router.get("/").handler(webHandler::home);
        router.get("/login").handler(webHandler::loginPage);
        router.get("/signup").handler(webHandler::signupPage);
        router.get("/dashboard").handler(webHandler::dashboardPage);
        router.get("/trip/:tripId").handler(webHandler::tripPage);
        router.get("/create-trip").handler(webHandler::createTripPage);

        // API routes for AJAX calls
        router.post("/api/signup").handler(authHandler::signup);
        router.post("/api/login").handler(authHandler::login);

        // Protected API routes
        router.route("/api/trips*").handler(JWTAuthHandler.create(jwtAuth));
        router.post("/api/trips").handler(tripHandler::createTrip);
        router.put("/api/trips/:tripId").handler(tripHandler::updateTrip);
        router.delete("/api/trips/:tripId").handler(tripHandler::deleteTrip);
        router.post("/api/trips/:tripId/days").handler(tripHandler::addDay);
        router.put("/api/trips/:tripId/days/:dayNumber").handler(tripHandler::updateDay);
        router.delete("/api/trips/:tripId/days/:dayNumber").handler(tripHandler::deleteDay);
        router.get("/api/dashboard").handler(tripHandler::getAllTrips);

        // Health check endpoint
        router.get("/health").handler(ctx -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("status", "healthy").encode());
        });

        // Start the server
        server.requestHandler(router).listen(8888, result -> {
            if (result.succeeded()) {
                System.out.println("Travel Itinerary Planner started on http://localhost:8888");
                startPromise.complete();
            } else {
                startPromise.fail(result.cause());
            }
        });
    }

    @Override
    public void stop() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}