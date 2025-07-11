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

class ValidationUtils {
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
                        .end(ErrorResponse.create(500, "Failed to retrieve trips").encode());
            }
        });
    }

    public void createTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        JsonObject body = ctx.getBodyAsJson();

        if (body == null || !body.containsKey("tripName")) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Trip name is required").encode());
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
                ctx.response().setStatusCode(500)
                        .end(ErrorResponse.create(500, "Failed to create trip").encode());
            }
        });
    }

    public void deleteTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");

        if (!ValidationUtils.isValidTripId(tripId)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid trip ID").encode());
            return;
        }

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        mongoClient.removeDocument(TRIPS_COLLECTION, query, res -> {
            if (res.succeeded() && res.result().getRemovedCount() > 0) {
                ctx.response().setStatusCode(200)
                        .end(new JsonObject().put("message", "Trip deleted").encode());
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip not found").encode());
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
                    .end(ErrorResponse.create(400, "dayNumber, date, and places are required").encode());
            return;
        }

        JsonArray places = body.getJsonArray("places");

        for (int i = 0; i < places.size(); i++) {
            JsonObject place = places.getJsonObject(i);
            if (place.getString("activity") == null || place.getString("time") == null) {
                ctx.response().setStatusCode(400)
                        .end(ErrorResponse.create(400, "activity and time are required for each place").encode());
                return;
            }
        }

        // Check if day already exists
        JsonObject checkQuery = new JsonObject().put("_id", tripId).put("userId", userId)
                .put("days.dayNumber", body.getInteger("dayNumber"));

        mongoClient.findOne("trips", checkQuery, null, res -> {
            if (res.succeeded() && res.result() != null) {
                // Day already exists
                ctx.response().setStatusCode(400)
                        .end(ErrorResponse.create(400, "Day number already exists for this trip").encode());
            } else {
                // Day doesn't exist, proceed to add it
                JsonObject newDay = new JsonObject()
                        .put("dayNumber", body.getInteger("dayNumber"))
                        .put("date", body.getString("date"))
                        .put("places", places);

                JsonObject updateQuery = new JsonObject().put("_id", tripId).put("userId", userId);

                JsonObject update = new JsonObject()
                        .put("$push", new JsonObject().put("days", newDay))
                        .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

                System.out.println("Update query: " + updateQuery.encodePrettily());
                System.out.println("Update operation: " + update.encodePrettily());

                mongoClient.updateCollection("trips", updateQuery, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        System.out.println("Mongo update succeeded. Matched: " + updateRes.result().getDocMatched());
                        ctx.response().setStatusCode(200)
                                .end(new JsonObject().put("message", "Day added").encode());
                    } else {
                        System.out.println("Error during MongoDB update: " + updateRes.cause().getMessage());
                        ctx.response().setStatusCode(500)
                                .end(ErrorResponse.create(500, "Failed to add day").encode());
                    }
                });
            }
        });
    }
    //Update options
    public void updateTrip(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        JsonObject body = ctx.getBodyAsJson();

        if (!ValidationUtils.isValidTripId(tripId)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid trip ID").encode());
            return;
        }

        if (body == null || (!body.containsKey("tripName") && !body.containsKey("description"))) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "At least tripName or description is required").encode());
            return;
        }

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);
        JsonObject updateFields = new JsonObject().put("updatedAt", System.currentTimeMillis());

        if (body.containsKey("tripName")) {
            updateFields.put("tripName", body.getString("tripName"));
        }
        if (body.containsKey("description")) {
            updateFields.put("description", body.getString("description"));
        }

        JsonObject update = new JsonObject().put("$set", updateFields);

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                ctx.response().setStatusCode(200)
                        .end(new JsonObject().put("message", "Trip updated successfully").encode());
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip not found").encode());
            }
        });
    }

    public void updateDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");
        JsonObject body = ctx.getBodyAsJson();

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        if (body == null || !body.containsKey("date")) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Date is required").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("days.$.date", body.getString("date"))
                        .put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                if (res.result().getDocModified() > 0) {
                    ctx.response().setStatusCode(200)
                            .end(new JsonObject().put("message", "Day updated successfully").encode());
                } else {
                    ctx.response().setStatusCode(404)
                            .end(ErrorResponse.create(404, "Day not found").encode());
                }
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip or day not found").encode());
            }
        });
    }

    public void addActivity(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");
        JsonObject body = ctx.getBodyAsJson();

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        if (body == null || !body.containsKey("activity") || !body.containsKey("time")) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Activity and time are required").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);

        JsonObject newActivity = new JsonObject()
                .put("activity", body.getString("activity"))
                .put("time", body.getString("time"));

        // Add optional fields if present
        if (body.containsKey("location")) {
            newActivity.put("location", body.getString("location"));
        }
        if (body.containsKey("notes")) {
            newActivity.put("notes", body.getString("notes"));
        }

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        JsonObject update = new JsonObject()
                .put("$push", new JsonObject().put("days.$.places", newActivity))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                ctx.response().setStatusCode(200)
                        .end(new JsonObject().put("message", "Activity added successfully").encode());
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip or day not found").encode());
            }
        });
    }

    public void updateActivity(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");
        String oldActivityName = ctx.pathParam("activityName");
        JsonObject body = ctx.getBodyAsJson();

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        if (body == null || (!body.containsKey("activity") && !body.containsKey("time")
                && !body.containsKey("location") && !body.containsKey("notes"))) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "At least one field (activity, time, location, notes) is required").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);

        // First, find the trip and get the current day data
        JsonObject findQuery = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        mongoClient.findOne(TRIPS_COLLECTION, findQuery, null, findRes -> {
            if (findRes.succeeded() && findRes.result() != null) {
                JsonObject trip = findRes.result();
                JsonArray days = trip.getJsonArray("days");

                // Find the specific day and activity
                for (int i = 0; i < days.size(); i++) {
                    JsonObject day = days.getJsonObject(i);
                    if (day.getInteger("dayNumber") == dayNumber) {
                        JsonArray places = day.getJsonArray("places");
                        for (int j = 0; j < places.size(); j++) {
                            JsonObject place = places.getJsonObject(j);
                            if (oldActivityName.equals(place.getString("activity"))) {
                                // Update the activity
                                if (body.containsKey("activity")) {
                                    place.put("activity", body.getString("activity"));
                                }
                                if (body.containsKey("time")) {
                                    place.put("time", body.getString("time"));
                                }
                                if (body.containsKey("location")) {
                                    place.put("location", body.getString("location"));
                                }
                                if (body.containsKey("notes")) {
                                    place.put("notes", body.getString("notes"));
                                }

                                // Update the entire day
                                JsonObject updateQuery = new JsonObject()
                                        .put("_id", tripId)
                                        .put("userId", userId)
                                        .put("days.dayNumber", dayNumber);

                                JsonObject update = new JsonObject()
                                        .put("$set", new JsonObject()
                                                .put("days.$.places", places)
                                                .put("updatedAt", System.currentTimeMillis()));

                                mongoClient.updateCollection(TRIPS_COLLECTION, updateQuery, update, updateRes -> {
                                    if (updateRes.succeeded()) {
                                        ctx.response().setStatusCode(200)
                                                .end(new JsonObject().put("message", "Activity updated successfully").encode());
                                    } else {
                                        ctx.response().setStatusCode(500)
                                                .end(ErrorResponse.create(500, "Failed to update activity").encode());
                                    }
                                });
                                return;
                            }
                        }
                        // Activity not found
                        ctx.response().setStatusCode(404)
                                .end(ErrorResponse.create(404, "Activity not found").encode());
                        return;
                    }
                }
                // Day not found
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Day not found").encode());
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip not found").encode());
            }
        });
    }

    public void getTripById(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");

        if (!ValidationUtils.isValidTripId(tripId)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid trip ID").encode());
            return;
        }

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        mongoClient.findOne(TRIPS_COLLECTION, query, null, result -> {
            if (result.succeeded() && result.result() != null) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                                .put("trip", result.result())
                                .encode());
            } else {
                ctx.response()
                        .setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip not found").encode());
            }
        });
    }

    public void reorderActivities(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");
        JsonObject body = ctx.getBodyAsJson();

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        if (body == null || !body.containsKey("activities")) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Activities array is required").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);
        JsonArray newActivitiesOrder = body.getJsonArray("activities");

        JsonObject query = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        JsonObject update = new JsonObject()
                .put("$set", new JsonObject()
                        .put("days.$.places", newActivitiesOrder)
                        .put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                if (res.result().getDocModified() > 0) {
                    ctx.response().setStatusCode(200)
                            .end(new JsonObject().put("message", "Activities reordered successfully").encode());
                } else {
                    ctx.response().setStatusCode(404)
                            .end(ErrorResponse.create(404, "Day not found").encode());
                }
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip or day not found").encode());
            }
        });
    }

    public void deleteDay(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);

        JsonObject query = new JsonObject().put("_id", tripId).put("userId", userId);

        JsonObject update = new JsonObject()
                .put("$pull", new JsonObject().put("days", new JsonObject().put("dayNumber", dayNumber)))
                .put("$set", new JsonObject().put("updatedAt", System.currentTimeMillis()));

        mongoClient.updateCollection(TRIPS_COLLECTION, query, update, res -> {
            if (res.succeeded() && res.result().getDocMatched() > 0) {
                if (res.result().getDocModified() > 0) {
                    ctx.response().setStatusCode(200)
                            .end(new JsonObject().put("message", "Day deleted").encode());
                } else {
                    ctx.response().setStatusCode(404)
                            .end(ErrorResponse.create(404, "Day not found").encode());
                }
            } else {
                ctx.response().setStatusCode(404)
                        .end(ErrorResponse.create(404, "Trip not found").encode());
            }
        });
    }

    public void deleteActivity(RoutingContext ctx) {
        String userId = getUserIdFromToken(ctx);
        String tripId = ctx.pathParam("tripId");
        String dayNumberStr = ctx.pathParam("dayNumber");
        String activityToRemove = ctx.pathParam("activityName");

        if (!ValidationUtils.isValidDayNumber(dayNumberStr)) {
            ctx.response().setStatusCode(400)
                    .end(ErrorResponse.create(400, "Invalid day number").encode());
            return;
        }

        int dayNumber = Integer.parseInt(dayNumberStr);

        System.out.println("Deleting activity: " + activityToRemove + " from day " + dayNumber + " in trip " + tripId);

        // First, find the trip and verify it exists with the specific day
        JsonObject findQuery = new JsonObject()
                .put("_id", tripId)
                .put("userId", userId)
                .put("days.dayNumber", dayNumber);

        mongoClient.findOne(TRIPS_COLLECTION, findQuery, null, findRes -> {
            if (findRes.succeeded() && findRes.result() != null) {
                // Trip and day exist, now remove the activity
                JsonObject updateQuery = new JsonObject()
                        .put("_id", tripId)
                        .put("userId", userId)
                        .put("days.dayNumber", dayNumber);

                JsonObject update = new JsonObject()
                        .put("$pull", new JsonObject()
                                .put("days.$.places", new JsonObject()
                                        .put("activity", activityToRemove)))
                        .put("$set", new JsonObject()
                                .put("updatedAt", System.currentTimeMillis()));

                System.out.println("Update query: " + updateQuery.encodePrettily());
                System.out.println("Update operation: " + update.encodePrettily());

                mongoClient.updateCollection(TRIPS_COLLECTION, updateQuery, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        System.out.println("Activity deletion - Matched: " + updateRes.result().getDocMatched() +
                                ", Modified: " + updateRes.result().getDocModified());

                        if (updateRes.result().getDocModified() > 0) {
                            ctx.response().setStatusCode(200)
                                    .end(new JsonObject().put("message", "Activity removed").encode());
                        } else {
                            ctx.response().setStatusCode(404)
                                    .end(ErrorResponse.create(404, "Activity not found").encode());
                        }
                    } else {
                        System.out.println("Error during activity deletion: " + updateRes.cause().getMessage());
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