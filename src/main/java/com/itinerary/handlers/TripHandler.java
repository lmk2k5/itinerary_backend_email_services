package com.itinerary.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public class TripHandler {

    private final MongoClient mongoClient;
    private static final String TRIPS_COLLECTION = "trips";

    public TripHandler(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private String getUserIdFromToken(RoutingContext ctx) {
        return ctx.user().principal().getString("sub");
    }

    public void getAllTrips(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        JsonObject query = new JsonObject().put("userId", userId);

        mongoClient.find(TRIPS_COLLECTION, query, result -> {
            if (result.succeeded()) {
                JsonArray trips = new JsonArray(result.result());
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("trips", trips)
                                .put("count", trips.size())
                                .encode());
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to retrieve trips")
                                .encode());
            }
        });
    }

    public void createTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("tripName")) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Trip name is required")
                            .encode());
            return;
        }

        JsonObject newTrip = new JsonObject()
                .put("userId", userId)
                .put("tripName", body.getString("tripName"))
                .put("description", body.getString("description", ""))
                .put("days", new JsonArray())
                .put("createdAt", System.currentTimeMillis())
                .put("updatedAt", System.currentTimeMillis());

        mongoClient.insert(TRIPS_COLLECTION, newTrip, result -> {
            if (result.succeeded()) {
                newTrip.put("_id", result.result());
                ctx.response()
                        .setStatusCode(201)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("message", "Trip created successfully")
                                .put("trip", newTrip)
                                .encode());
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to create trip")
                                .encode());
            }
        });
    }

    public void updateTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        JsonObject body = ctx.getBodyAsJson();

        if (body == null) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Request body is required")
                            .encode());
            return;
        }

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("tripName", body.getString("tripName"))
                        .put("description", body.getString("description", ""))
                        .put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, result -> {
            if (result.succeeded()) {
                if (result.result().getDocMatched() > 0) {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Trip updated successfully")
                                    .encode());
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Trip not found")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to update trip")
                                .encode());
            }
        });
    }

    public void deleteTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId);

        mongoClient.removeDocument(TRIPS_COLLECTION, query, result -> {
            if (result.succeeded()) {
                if (result.result().getRemovedCount() > 0) {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Trip deleted successfully")
                                    .encode());
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Trip not found")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to delete trip")
                                .encode());
            }
        });
    }

    public void addDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("dayNumber")) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Day number is required")
                            .encode());
            return;
        }

        JsonObject newDay = new JsonObject()
                .put("dayNumber", body.getInteger("dayNumber"))
                .put("date", body.getString("date", ""))
                .put("places", body.getJsonArray("places", new JsonArray()));

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$push", new JsonObject().put("days", newDay))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, result -> {
            if (result.succeeded()) {
                if (result.result().getDocMatched() > 0) {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Day added successfully")
                                    .put("day", newDay)
                                    .encode());
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Trip not found")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to add day")
                                .encode());
            }
        });
    }

    public void updateDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumber = ctx.pathParam("dayNumber");
        JsonObject body = ctx.getBodyAsJson();

        if (body == null) {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject()
                            .put("error", "Request body is required")
                            .encode());
            return;
        }

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", Integer.parseInt(dayNumber));

        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("days.$.date", body.getString("date", ""))
                        .put("days.$.places", body.getJsonArray("places", new JsonArray()))
                        .put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, result -> {
            if (result.succeeded()) {
                if (result.result().getDocMatched() > 0) {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Day updated successfully")
                                    .encode());
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Trip or day not found")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to update day")
                                .encode());
            }
        });
    }

    public void deleteDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumber = ctx.pathParam("dayNumber");

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject()
                        .put("days", new JsonObject()
                                .put("dayNumber", Integer.parseInt(dayNumber))))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, result -> {
            if (result.succeeded()) {
                if (result.result().getDocMatched() > 0) {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Day deleted successfully")
                                    .encode());
                } else {
                    ctx.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject()
                                    .put("error", "Trip not found")
                                    .encode());
                }
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("error", "Failed to delete day")
                                .encode());
            }
        });
    }
}