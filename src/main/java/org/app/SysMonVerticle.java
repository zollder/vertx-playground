package org.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.stream.Collectors;

import org.app.model.Whiskey;

public class SysMonVerticle extends AbstractVerticle {

	public static final String COLLECTION = "whiskies";
	private MongoClient client;

	@Override
	public void start(Future<Void> future) {
		JsonObject mongoConfig = new JsonObject()
			.put("http.port", 8082)
			.put("db_name", "whiskies")
			.put("connection_string", "mongodb://localhost:27017");
		client = MongoClient.createNonShared(vertx, mongoConfig);

		// creates data, starts HTTP server
		createData((nothing) -> startWebApp((http) -> completeStartup(http, future)), future);
	}

	@Override
	public void stop() throws Exception {
		// close JDBC client
		this.client.close();
	}

	/**
	 * Creates router object and configures it to handle static resources.
	 * Defines REST endpoints.
	 * Creates and configures HTTP server and calls the next action step.
	 *
	 * @param next - next action step
	 */
	private void startWebApp(Handler<AsyncResult<HttpServer>> nextAction) {
		// Create a router object.
		Router router = Router.router(vertx);

		// Bind "/" to our hello message - so we are still compatible.
		router.route("/").handler(routingContext -> {
			HttpServerResponse response = routingContext.response();
			response
				.putHeader("content-type", "text/html")
				.end("<h1>Hello from my first Vert.x 3 application</h1>");
		});

		// Serve static resources from the /assets directory
		// routes requests on "/assets/" to resources stored in assets directory
		router.route("/assets/*").handler(StaticHandler.create("assets"));

		/* REST endpoints: */
		router.get("/api/whiskies").handler(this::getAll);
		router.route("/api/whiskies*").handler(BodyHandler.create());
		router.post("/api/whiskies").handler(this::addOne);
		router.get("/api/whiskies/:id").handler(this::getOne);
		router.put("/api/whiskies/:id").handler(this::updateOne);
		router.delete("/api/whiskies/:id").handler(this::deleteOne);

		// Create HTTP server and pass "accept" method to the request handler.
		vertx
			.createHttpServer()
			.requestHandler(router::accept)
			.listen(
				// Retrieve the port from the configuration, defaults to 8080
				config().getInteger("http.port", 8080),
				nextAction::handle
			);
	}

	/**
	 * Verifies if HTTP server has started
	 * Completes or fails the future based on the server startup result.
	 *
	 * @param httpServer - HTTP server result
	 * @param future - completion future to report success or failure
	 */
	private void completeStartup(AsyncResult<HttpServer> httpServer, Future<Void> future) {
		if (httpServer.succeeded()) {
			future.complete();
		} else {
			future.fail(httpServer.cause());
		}
	}

	private void getAll(RoutingContext routingContext) {
		client.find(COLLECTION, new JsonObject(), results -> {
			// get query results as a list of json objects and map them to a collection of products
			List<JsonObject> objects = results.result();
			List<Whiskey> products = objects.stream()
				.map(json -> new Whiskey(json))
				.collect(Collectors.toList());
			routingContext.response()
				.putHeader("content-type", "application-json; charset=utf-8")
				.end(Json.encodePrettily(products));
		});
	}

	private void addOne(RoutingContext routingContext) {
		final Whiskey product = Json.decodeValue(routingContext.getBodyAsString(), Whiskey.class);
		client.insert(COLLECTION, product.toJson(), asyncResult -> {
			routingContext.response()
				.setStatusCode(201)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(Json.encodePrettily(product.setId(asyncResult.result())));
		});
	}

	private void getOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			client.findOne(COLLECTION, new JsonObject().put("_id", id),  null, asyncResult -> {
				if (asyncResult.succeeded()) {
					if (asyncResult.result() == null) {
						routingContext.response().setStatusCode(404).end();
						return;
					} else {
						routingContext.response()
							.setStatusCode(200)
							.putHeader("content-type", "application/json; charset=utf-8")
							.end(Json.encodePrettily(new Whiskey(asyncResult.result())));
					}
				} else {
					routingContext.response().setStatusCode(404).end();
				}
			});
		}
	}

	@SuppressWarnings("deprecation")
	private void updateOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		JsonObject json = routingContext.getBodyAsJson();
		if (id == null || json == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			client.update(COLLECTION,
					new JsonObject().put("_id", id),
					new JsonObject().put("$set", json),
					asyncResult -> {
						if (asyncResult.failed()) {
							routingContext.response().setStatusCode(404).end();
						} else {
							routingContext.response()
								.putHeader("content-type", "application/json; charset=utf-8")
								.end(Json.encodePrettily(new Whiskey(id, json.getString("name"), json.getString("origin"))));
						}
					});
		}
	}

	@SuppressWarnings("deprecation")
	private void deleteOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			client.removeOne(COLLECTION,
					new JsonObject().put("_id", id),
					asyncResult -> routingContext.response().setStatusCode(204).end()
			);
		}
	}

	private void createData(Handler<AsyncResult<Void>> nextAction, Future<Void> future) {
		// initial documents
		Whiskey bowmore = new Whiskey("Bowmore 15 Years Laimrig", "Scotland, Islay");
		Whiskey talisker = new Whiskey("Talisker 57° North", "Scotland, Island");
		System.out.println(bowmore.toJson());

		client.count(COLLECTION, new JsonObject(), count -> {
			if (count.succeeded()) {
				if (count.result() == 0) {
					// insert data, if empty
					client.insert(COLLECTION, bowmore.toJson(), asyncResult1 -> {
						if (asyncResult1.failed()) {
							future.fail(asyncResult1.cause());
						} else {
							client.insert(COLLECTION, talisker.toJson(), asyncResult2 -> {
								if (asyncResult2.failed()) {
									future.fail(asyncResult2.cause());
								} else {
									nextAction.handle(Future.succeededFuture());
								}
							});
						}
					});
				} else {
					nextAction.handle(Future.succeededFuture());
				}
			} else {
				future.fail(count.cause());
			}
		});
	}
}