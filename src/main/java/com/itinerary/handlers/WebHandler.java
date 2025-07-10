package com.itinerary.handlers;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

public class WebHandler {

    private final MongoClient mongoClient;
    private final JWTAuth jwtAuth;
    private final ThymeleafTemplateEngine templateEngine;

    public WebHandler(MongoClient mongoClient, JWTAuth jwtAuth, ThymeleafTemplateEngine templateEngine) {
        this.mongoClient = mongoClient;
        this.jwtAuth = jwtAuth;
        this.templateEngine = templateEngine;
    }

    public void home(RoutingContext ctx) {
        // Check if user is logged in via JWT cookie
        String token = ctx.getCookie("jwt_token") != null ? ctx.getCookie("jwt_token").getValue() : null;

        if (token != null) {
            // Validate token and redirect to dashboard
            jwtAuth.authenticate(new JsonObject().put("jwt", token), authResult -> {
                if (authResult.succeeded()) {
                    ctx.response().setStatusCode(302).putHeader("Location", "/dashboard").end();
                } else {
                    renderTemplate(ctx, "templates/index.html", new JsonObject());
                }
            });
        } else {
            renderTemplate(ctx, "templates/index.html", new JsonObject());
        }
    }

    public void loginPage(RoutingContext ctx) {
        renderTemplate(ctx, "templates/login.html", new JsonObject());
    }

    public void signupPage(RoutingContext ctx) {
        renderTemplate(ctx, "templates/signup.html", new JsonObject());
    }

    public void dashboardPage(RoutingContext ctx) {
        String token = ctx.getCookie("jwt_token") != null ? ctx.getCookie("jwt_token").getValue() : null;

        if (token == null) {
            ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            return;
        }

        // Validate token
        jwtAuth.authenticate(new JsonObject().put("jwt", token), authResult -> {
            if (authResult.succeeded()) {
                JsonObject user = authResult.result().principal();
                JsonObject context = new JsonObject()
                        .put("username", user.getString("username"))
                        .put("userId", user.getString("sub"));

                renderTemplate(ctx, "templates/dashboard.html", context);
            } else {
                ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            }
        });
    }

    public void tripPage(RoutingContext ctx) {
        String token = ctx.getCookie("jwt_token") != null ? ctx.getCookie("jwt_token").getValue() : null;
        String tripId = ctx.pathParam("tripId");

        if (token == null) {
            ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            return;
        }

        // Validate token
        jwtAuth.authenticate(new JsonObject().put("jwt", token), authResult -> {
            if (authResult.succeeded()) {
                JsonObject user = authResult.result().principal();
                JsonObject context = new JsonObject()
                        .put("username", user.getString("username"))
                        .put("userId", user.getString("sub"))
                        .put("tripId", tripId);

                renderTemplate(ctx, "templates/trip.html", context);
            } else {
                ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            }
        });
    }

    public void createTripPage(RoutingContext ctx) {
        String token = ctx.getCookie("jwt_token") != null ? ctx.getCookie("jwt_token").getValue() : null;

        if (token == null) {
            ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            return;
        }

        // Validate token
        jwtAuth.authenticate(new JsonObject().put("jwt", token), authResult -> {
            if (authResult.succeeded()) {
                JsonObject user = authResult.result().principal();
                JsonObject context = new JsonObject()
                        .put("username", user.getString("username"))
                        .put("userId", user.getString("sub"));

                renderTemplate(ctx, "templates/create-trip.html", context);
            } else {
                ctx.response().setStatusCode(302).putHeader("Location", "/login").end();
            }
        });
    }

    private void renderTemplate(RoutingContext ctx, String templateName, JsonObject context) {
        templateEngine.render(context, templateName, res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("Content-Type", "text/html")
                        .end(res.result());
            } else {
                ctx.response()
                        .setStatusCode(500)
                        .end("Template rendering failed: " + res.cause().getMessage());
            }
        });
    }
}