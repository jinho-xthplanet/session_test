package com.test;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * Snackworld Rest Server
 * @author Jinho Choi
 *
 */
public class Server extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Server.class.getName());
	private EventBus eb;
	
	private SessionStore sessionStore;
	private HttpServer server;
	private JsonObject config;
	final private int IdleTimeout = 10;
	final private int sessionIdleTimeout =3600000;
		
	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		config = context.config();
		eb = vertx.eventBus();
	}

	@Override
	public void start(Future<Void> future) throws Exception {
		
		// Session Store
//		sessionStore = ClusteredSessionStore.create(vertx);
		sessionStore = LocalSessionStore.create(vertx);
		
		if(sessionStore == null) {
			log.error("sessionStore is null");
		}
		else {
			log.info("sessionStore created");
		}
		
		final Router router = createRouter();
		// HTTP Server Start
		Future<HttpServer> serverFuture = Future.future();
		HttpServerOptions httpOptions = new HttpServerOptions()
											.setPort(80)
											.setTcpKeepAlive(true)
											.setIdleTimeout(IdleTimeout)
											;
		server = vertx.createHttpServer(httpOptions);
		// API Router
		server.requestHandler(router::accept);
		server.listen(80, serverFuture.completer());
		
		// Handler
		serverFuture.setHandler(done -> {
			if (done.succeeded()) {
				log.info("Vertx http/https server successfully started !");
				future.complete();
			} else {
			    // At least one server failed
				log.error("Vertx http/https server starting failed ! : " + done.cause().getMessage());
				future.failed();
			}
		});
	}
	
	@Override
	public void stop(Future<Void> future) {
		if (server == null) {
			log.info("# https server stopped successfully !");
			future.complete();
		}
		// Close HTTP/HTTPS Server
		else {
			Future<Void> serverFuture = Future.future();
			server.close(serverFuture.completer());
			
			serverFuture.setHandler(ar -> {
				if (ar.succeeded()) {
					log.info("# https server stopped successfully !");
					future.complete();
				}
				else {
					log.error("! https server Stop error : " + ar.cause().toString());
					future.fail(ar.cause().toString());
				}
			});
		}
	}
	
	/**
	 * Create router
	 * @return Router
	 */
	private Router createRouter() {
		Router router = Router.router(vertx);
		
		// session handler
		SessionHandler sessionHandler = SessionHandler.create(sessionStore);
		router.route().handler(sessionHandler);
		
		// failure handler
		router.route().failureHandler(ErrorHandler.create(true));
		// Access Log
		router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
		// exception handler
		router.route().handler(context -> {
			context.request().exceptionHandler(hand -> {
				log.error("### context.request().exceptionHandler : " + hand.getMessage());
				if(!context.response().closed()) {
					context.response().close();
				}
				context.request().connection().close();
			});
			
			context.request().connection().exceptionHandler(hand -> {
				if(!context.response().closed()) {
					context.response().close();
				}
				context.request().connection().close();
			});
			
			context.next();
		});

		// version 0
		router.mountSubRouter("/v0", version0());
		
		return router;
	}
	
	/**
	 * Version 0
	 * @return Router
	 */
	private Router version0() {
		Router router = Router.router(vertx);
		router.route().consumes("application/json").consumes("text/plain");
		router.route().produces("application/json").produces("text/plain");
		router.route().handler(BodyHandler.create());
		router.route().handler(context -> {
			context.response().headers().add(CONTENT_TYPE, "application/json; charset=utf-8");
			context.next();
		});
		
		// CORS Configuration
		CorsHandler corsHandler = CorsHandler.create("*");
		corsHandler.allowCredentials(false);
		corsHandler.allowedMethod(HttpMethod.OPTIONS);
		corsHandler.allowedMethod(HttpMethod.GET);
		corsHandler.allowedMethod(HttpMethod.POST);
		corsHandler.allowedMethod(HttpMethod.PUT);
		corsHandler.allowedMethod(HttpMethod.DELETE);
		corsHandler.allowedHeader("Authorization");
		corsHandler.allowedHeader("www-authenticate");		 
		corsHandler.allowedHeader("Content-Type");
		router.route().handler(corsHandler);
	    
		// ping
		router.post("/login").handler(this::login);
		router.post("/info").handler(this::info);
		
		return router;
	}
	
	/**
	 * Account
	 * @param RoutingContext ctx
	 */
	private void login(RoutingContext ctx) {
		final JsonObject req = ctx.getBodyAsJson();
		final String userKey = req.getString("userKey");
		
		// make new session
	    makeSessionAndResponse(userKey, ctx);
	}
	
	/**
	 * Make new session and response to http.
	 * @param String userKey
	 * @param JsonObject result
	 * @param RoutingContext ctx
	 */
	private void makeSessionAndResponse(String userKey, RoutingContext ctx) {
		final Session vertxSession = sessionStore.createSession(sessionIdleTimeout);
	    sessionStore.put(vertxSession, done -> {
	    	if(done.succeeded()) {
	    		log.debug("userKey : " + userKey);
	    		
	    		vertxSession.put("userKey", userKey);
			    final String sessionId = vertxSession.id();
			    
			    log.info("new sessionId : " + sessionId);
			    // response
    			final JsonObject res = new JsonObject()
    					.put("code", 0)
    					.put("sessionId", sessionId)
    					.put("userKey", userKey);
			    ctx.response().setStatusCode(200).end(res.encode());
	    	}
	    	else {
	    		log.error("session store fail", done.cause());
	    		// response
	    		final JsonObject res = new JsonObject().
    					put("code", -1);
	    		ctx.response().setStatusCode(500).end(res.encode());
	    	}
	    });
	}
	
	/**
	 * Account
	 * @param RoutingContext ctx
	 */
	private void info(RoutingContext ctx) {
		final JsonObject req = ctx.getBodyAsJson();
		final String sessionId = req.getString("sessionId");
		
		// get session
		sessionStore.get(sessionId, res -> {
			if(res.succeeded()) {
				final Session session = res.result();
				// check session
				if(session != null) {
					log.info("session : " + session.data());
					ctx.response().setStatusCode(200).end(session.data().toString());;
				}
				else {
					log.error("session not exist");
					ctx.response().setStatusCode(401).end();
				}
			}
			else {
				log.error("get session fail", res.cause());
				ctx.response().setStatusCode(500).end();;
			}
		});
	}
	
}