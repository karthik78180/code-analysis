package com.example.consumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Example Verticle that uses wrapper libraries.
 *
 * This project will have dependency analysis run on:
 * - com.yourcompany.wrappers:vertx-utils
 * - com.yourcompany.wrappers:database-helper
 *
 * If those wrapper libraries contain violations (e.g., direct Vertx.deployVerticle usage,
 * internal API usage, mutable static fields), the build will fail.
 */
public class MyVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        vertx.createHttpServer()
            .requestHandler(req -> {
                // This would be fine - it's in the application code
                req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x!");
            })
            .listen(8080)
            .onComplete(http -> {
                if (http.succeeded()) {
                    startPromise.complete();
                    System.out.println("HTTP server started on port 8080");
                } else {
                    startPromise.fail(http.cause());
                }
            });
    }
}
