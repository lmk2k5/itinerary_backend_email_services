package com.itinerary.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

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

    /*public void addDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("dayNumber") || !body.containsKey("date")
                || !body.containsKey("time") || !body.containsKey("activity")) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "dayNumber, date, time, and activity are required").encode());
            return;
        }

        JsonObject newDay = new JsonObject()
                .put("dayNumber", body.getInteger("dayNumber"))
                .put("date", body.getString("date"))
                .put("time", body.getString("time"))
                .put("activity", body.getString("activity"));

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$push", new JsonObject().put("days", newDay))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Day added").encode());
            } else {
                ctx.response().setStatusCode(404).end(new JsonObject().put("error", "Trip not found").encode());
            }
        });
    }*/
    public void addDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);  // Extract the userId from the token
        String tripId = ctx.pathParam("tripId");  // Get the tripId from the URL path
        JsonObject body = ctx.getBodyAsJson();  // Get the request body

        // Validate that the body contains required fields
        if (body == null || !body.containsKey("dayNumber") || !body.containsKey("date")
                || !body.containsKey("places")) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "dayNumber, date, and places are required").encode());
            return;
        }

        // Extract the places array from the body
        JsonArray places = body.getJsonArray("places");

        // Validate each place to ensure 'activity' and 'time' are present
        for (int i = 0; i < places.size(); i++) {
            JsonObject place = places.getJsonObject(i);
            if (place.getString("activity") == null || place.getString("time") == null) {
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "activity and time are required for each place").encode());
                return;
            }
        }

        // Query to check if the dayNumber already exists in the trip's days array
        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId)
                .put("days.dayNumber", body.getInteger("dayNumber"));

        // Check if the day already exists in the trip
        mongoClient.findOne("trips", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                // If a day with the same dayNumber exists, return an error
                ctx.response().setStatusCode(400)
                        .end(new JsonObject().put("error", "Day number already exists for this trip").encode());
            } else {
                // If day doesn't exist, proceed to add it
                JsonObject newDay = new JsonObject()
                        .put("dayNumber", body.getInteger("dayNumber"))
                        .put("date", body.getString("date"))
                        .put("places", places);  // Store the places array as part of the new day

                // Define the update operation to push the new day into the days array
                JsonObject update = new JsonObject()
                        .put("$push", new JsonObject().put("days", newDay))
                        .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

                // Perform the update in the database
                mongoClient.updateCollection("trips", query, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Day added").encode());
                    } else {
                        ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to add day").encode());
                    }
                });
            }
        });
    }


    //added this rn
    public void deleteActivity(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);  // Extract the userId from the token
        String tripId = ctx.pathParam("tripId");  // Get the tripId from the URL path
        Integer dayNumber = Integer.valueOf(ctx.pathParam("dayNumber"));  // Get the dayNumber from the path
        String activityToRemove = ctx.pathParam("activityName");  // Get the activity name to remove (from path param)

        // Define the query to find the trip by tripId and userId
        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        // Find the specific day and remove the activity from it
        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject().put("days", new JsonObject()
                        .put("dayNumber", dayNumber)
                        .put("places", new JsonObject().put("activity", activityToRemove))))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        // Perform the update in the database
        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Activity removed").encode());
            } else {
                ctx.response().setStatusCode(404).end(new JsonObject().put("error", "Trip or day not found").encode());
            }
        });
    }

    public void deleteDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        int dayNumber = Integer.parseInt(ctx.pathParam("dayNumber"));

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject().put("days", new JsonObject().put("dayNumber", dayNumber)))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Day deleted").encode());
            } else {
                ctx.response().setStatusCode(404).end(new JsonObject().put("error", "Trip not found").encode());
            }
        });
    }
}
