package com.itinerary;

import com.itinerary.handlers.AuthHandler;
import com.itinerary.handlers.TripHandler;
import com.itinerary.handlers.WebHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.HashSet;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "itinerary_app");

        MongoClient mongoClient = MongoClient.createShared(vertx, mongoConfig);

        // Simplified JWT configuration for Vert.x 4.5.1
        JWTAuthOptions jwtOptions = new JWTAuthOptions()
                .addJwk(new JsonObject()
                        .put("kty", "oct")
                        .put("k", "c3VwZXJzZWNyZXRrZXk=")); // Base64 encoded "supersecretkey"

        JWTAuth jwtAuth = JWTAuth.create(vertx, jwtOptions);

        Router router = Router.router(vertx);

        // Enable CORS
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("Authorization");

        router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders));
        router.route().handler(BodyHandler.create());

        AuthHandler authHandler = new AuthHandler(mongoClient, jwtAuth);
        TripHandler tripHandler = new TripHandler(mongoClient);
        WebHandler webHandler = new WebHandler(vertx); // Fixed: passing vertx parameter

        // Auth routes
        router.post("/auth/signup").handler(authHandler::signup);
        router.post("/auth/login").handler(authHandler::login);

        // Static test pages
        router.get("/test-login").handler(webHandler::serveLoginPage);
        router.get("/test-signup").handler(webHandler::serveSignupPage);
        router.get("/test-dashboard").handler(webHandler::serveDashboardPage);

        // Static resources
        router.route("/css/*").handler(StaticHandler.create("webroot"));
        router.route("/js/*").handler(StaticHandler.create("webroot"));

        // Secure API
        router.route("/api/*").handler(JWTAuthHandler.create(jwtAuth));

        // Trip routes
        router.get("/api/dashboard").handler(tripHandler::getAllTrips);
        router.post("/api/trips").handler(tripHandler::createTrip);
        router.delete("/api/trips/:tripId").handler(tripHandler::deleteTrip);
        router.post("/api/trips/:tripId/days").handler(tripHandler::addDay);
        router.delete("/api/trips/:tripId/days/:dayNumber").handler(tripHandler::deleteDay);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888)
                .onSuccess(server -> {
                    System.out.println("HTTP server started on port " + server.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
}