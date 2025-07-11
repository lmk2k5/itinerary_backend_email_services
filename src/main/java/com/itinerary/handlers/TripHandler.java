package com.itinerary.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

class ErrorResponse {
    public static JsonObject create(int code, String message) {
        return new JsonObject()
                .put("error", true)
                .put("code", code)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis());
    }
}

public class TripHandler {

    private final MongoClient mongoClient;
    private static final String TRIPS_COLLECTION = "trips";

    public TripHandler(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private String getUserIdFromToken(RoutingContext ctx) {
        if (ctx.user() == null || ctx.user().principal() == null) {
            throw new RuntimeException("Unauthorized: No user in context");
        }
        return ctx.user().principal().getString("sub");
    }

    public void getAllTrips(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        JsonObject query = new JsonObject().put("userId", userId);

        mongoClient.find(TRIPS_COLLECTION, query, result -> {
            if (result.succeeded()) {
                JsonArray trips = new JsonArray(result.result());
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("trips", trips)
                                .encode());
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to retrieve trips").encode());
            }
        });
    }

    public void createTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("tripName")) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Trip name is required").encode());
            return;
        }

        JsonObject newTrip = new JsonObject()
                .put("userId", userId)
                .put("tripName", body.getString("tripName"))
                .put("description", body.getString("description", ""))
                .put("days", new JsonArray())
                .put("createdAt", System.currentTimeMillis())
                .put("updatedAt", System.currentTimeMillis());

        mongoClient.insert(TRIPS_COLLECTION, newTrip, res -> {
            if (res.succeeded()) {
                newTrip.put("_id", res.result());
                ctx.response()
                        .setStatusCode(201)
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("trip", newTrip).encode());
            } else {
                ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to create trip").encode());
            }
        });
    }

    public void deleteTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        mongoClient.removeDocument(TRIPS_COLLECTION, query, res -> {
            if (res.succeeded() && res.result().getRemovedCount() > 0) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Trip deleted").encode());
            } else {
                ctx.response().setStatusCode(404).end(new JsonObject().put("error", "Trip not found").encode());
            }
        });
    }

    public void addDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        JsonObject body = ctx.getBodyAsJson();

        System.out.println("Incoming request body for addDay: " + body.encodePrettily());

        if (body == null || !body.containsKey("dayNumber") || !body.containsKey("date")
                || !body.containsKey("places")) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "dayNumber, date, and places are required").encode());
            return;
        }

        JsonArray places = body.getJsonArray("places");

        for (int i = 0; i < places.size(); i++) {
            JsonObject place = places.getJsonObject(i);
            if (place.getString("activity") == null || place.getString("time") == null) {
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "activity and time are required for each place").encode());
                return;
            }
        }

        // FIXED: Separate query for checking if day exists vs updating
        JsonObject checkQuery = new JsonObject().put("_id", tripId).put("userId", userId)
                .put("days.dayNumber", body.getInteger("dayNumber"));

        mongoClient.findOne("trips", checkQuery, null, res -> {
            if (res.succeeded() && res.result() != null) {
                // Day already exists
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "Day number already exists for this trip").encode());
            } else {
                // Day doesn't exist, proceed to add it
                JsonObject newDay = new JsonObject()
                        .put("dayNumber", body.getInteger("dayNumber"))
                        .put("date", body.getString("date"))
                        .put("places", places);

                // FIXED: Use a different query for the update - just match tripId and userId
                JsonObject updateQuery = new JsonObject().put("_id", tripId).put("userId", userId);

                JsonObject update = new JsonObject()
                        .put("$push", new JsonObject().put("days", newDay))
                        .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

                System.out.println("Update query: " + updateQuery.encodePrettily());
                System.out.println("Update operation: " + update.encodePrettily());

                mongoClient.updateCollection("trips", updateQuery, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        System.out.println("Mongo update succeeded. Matched: " + updateRes.result().getDocMatched());
                        ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Day added").encode());
                    } else {
                        System.out.println("Error during MongoDB update: " + updateRes.cause().getMessage());
                        ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to add day").encode());
                    }
                });
            }
        });
    }

    public class ValidationUtils {
        public static boolean isValidTripId(String tripId) {
            return tripId != null && tripId.length() == 24; // MongoDB ObjectId length
        }

        public static boolean isValidDayNumber(String dayNumber) {
            try {
                int day = Integer.parseInt(dayNumber);
                return day > 0 && day <= 365; // Reasonable bounds
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public void deleteActivity(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");

        if (!ValidationUtils.isValidDayNumber(ctx.pathParam("dayNumber"))) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        Integer dayNumber = Integer.valueOf(ctx.pathParam("dayNumber"));
        String activityToRemove = ctx.pathParam("activityName");

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject()
                        .put("days.$.places", new JsonObject()
                                .put("activity", activityToRemove)));

        // First find the document to get array index
        JsonObject findQuery = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        mongoClient.findOne(TRIPS_COLLECTION, findQuery, null, res -> {
            if (res.succeeded() && res.result() != null) {
                // Now update using positional operator
                mongoClient.updateCollection(TRIPS_COLLECTION, findQuery, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        ctx.response().setStatusCode(200)
                                .end(new JsonObject().put("message", "Activity removed").encode());
                    } else {
                        ctx.response().setStatusCode(500)
                                .end(ErrorResponse.create(500, "Failed to remove activity").encode());
                    }
                });
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip or day not found").encode());
            }
        });
    }
}
