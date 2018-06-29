package com.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Main Class
 * @author Jinho Choi
 *
 */
public class Main extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Main.class.getName());
    private JsonObject config;
    
    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        // deploy http server
        final Future<String> serverFuture = Future.future();
        final DeploymentOptions serverOptions = new DeploymentOptions().setInstances(1).setConfig(config);
        vertx.deployVerticle(Server.class.getName(), serverOptions, serverFuture.completer());
        
        serverFuture.setHandler(done -> {
        	if(done.succeeded()) {
                log.info("http server deployed successfully!");
                future.complete();
            }
            else {
                log.info("http server deploy failed!", done.cause());
                future.fail(done.cause());
            }
        });
    }
    
    @Override
    public void stop(Future<Void> future) {
        log.info("### Snackworld server stopped successfully!");
        future.complete();
    }
}
