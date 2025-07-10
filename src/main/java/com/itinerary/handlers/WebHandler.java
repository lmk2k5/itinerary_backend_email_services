package com.itinerary.handlers;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.Vertx;

public class WebHandler {
    private final Vertx vertx;

    public WebHandler(Vertx vertx) {
        this.vertx = vertx;
    }

    public void serveLoginPage(RoutingContext ctx) {
        ctx.response().sendFile("static/html/test-login.html");
    }

    public void serveSignupPage(RoutingContext ctx) {
        ctx.response().sendFile("static/html/test-signup.html");
    }

    public void serveDashboardPage(RoutingContext ctx) {
        ctx.response().sendFile("static/html/test-dashboard.html");
    }
}