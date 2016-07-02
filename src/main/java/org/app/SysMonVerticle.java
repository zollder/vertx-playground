package org.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.app.model.Whiskey;

public class SysMonVerticle extends AbstractVerticle {

	private Map<Integer, Whiskey> products = new LinkedHashMap<>();
	private JDBCClient jdbc;

	@Override
	public void start(Future<Void> future) {
		// create an instance of JDBC client
		jdbc = JDBCClient.createShared(vertx, config(), "My-Whisky-Collection");

		// Create data
		this.createData();

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

		// Create the HTTP server and pass the "accept" method to the request handler.
		vertx
			.createHttpServer()
			.requestHandler(router::accept)
			.listen(
				// Retrieve the port from the configuration, defaults to 8080
				config().getInteger("http.port", 8080), result -> {
					if (result.succeeded()) {
						future.complete();
					} else {
						future.fail(result.cause());
					}
				}
			);

		/* REST endpoints: */

		/*
		 * Returns all whiskey bottles stored in memory

		 * Creates/adds a new whiskey bottle.
		 * Contains request body object, which has to be explicitly enabled for better performance.
		 * Enables reading the request body for all routes under “/api/whiskies” (can be globally enabled)
		 * and maps POST request on /api/whiskies to the addOne
		 */
		router.get("/api/whiskies").handler(this::getAll);
		router.route("/api/whiskies*").handler(BodyHandler.create());
		router.post("/api/whiskies").handler(this::addOne);
		router.get("/api/whiskies/:id").handler(this::getOne);
		router.put("/api/whiskies/:id").handler(this::updateOne);
		router.delete("/api/whiskies/:id").handler(this::deleteOne);
	}

	private void createData() {
		Whiskey bowmore = new Whiskey("Bowmore 15 Years Laimrig", "Scotland, Islay");
		products.put(bowmore.getId(), bowmore);
		Whiskey talisker = new Whiskey("Talisker 57° North", "Scotland, Island");
		products.put(talisker.getId(), talisker);
	}

	private void getAll(RoutingContext routingContext) {
		routingContext
			.response()
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(Json.encodePrettily(products.values()));
	}

	private void addOne(RoutingContext routingContext) {
		final Whiskey whiskey = Json.decodeValue(routingContext.getBodyAsString(), Whiskey.class);
		products.put(whiskey.getId(), whiskey);
		routingContext.response()
			.setStatusCode(201)
			.putHeader("content-type", "application-json; charset=utf-8")
			.end(Json.encodePrettily(whiskey));
	}

	private void deleteOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			Integer idAsInteger = Integer.valueOf(id);
			products.remove(idAsInteger);
		}
		routingContext.response().setStatusCode(204).end();
	}

	private void getOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			Whiskey product = products.get(Integer.valueOf(id));
			if (product != null) {
				routingContext.response()
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(Json.encodePrettily(product));
			} else {
				routingContext.response().setStatusCode(400).end();
			}
		}
	}

	private void updateOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		JsonObject json = routingContext.getBodyAsJson();
		if (id == null || json == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			Whiskey product = products.get(Integer.valueOf(id));
			if (product != null) {
				product.setName(json.getString("name"));
				product.setOrigin(json.getString("origin"));
				routingContext.response()
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(Json.encodePrettily(product));
			} else {
				routingContext.response().setStatusCode(404).end();
			}
		}
	}

	/**
	 * Retrieves an SQLConnection and calls the next step.
	 * @param next - next step
	 * @param future - completion future passed by vert.x to report success or failure
	 */
	private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> future) {
		jdbc.getConnection(connection -> {
			if (connection.failed()) {
				future.fail(connection.cause());
			} else {
				next.handle(Future.succeededFuture(connection.result()));
			}
		});
	}
	
/*	private void createData(AsyncResult<SQLConnection> result, Handler<AsyncResult<Void>> next, Future<Void> future) {
		if (result.failed()) {
			future.fail(result.cause());
		} else {
			SQLConnection connection = result.result();
			connection.execute(getCreateTableStatement(), asyncHandler -> {
				if (asyncHandler.failed()) {
					future.fail(asyncHandler.cause());
					connection.close();
					return;
				}
				
				connection.query("select * from Whiskey", select -> {
					if (select.failed()) {
						future.fail(asyncHandler.cause());
						connection.close();
						return;
					}
					
					if (select.result().getNumRows() == 0) {
						insert();
					}
				});
			});
		}
	}

	private String getCreateTableStatement() {
		return "CREATE TABLE IF NOT EXISTS Whiskey (id INTEGER IDENTITY, name varchar(100), origin varchar(100))";
	}

	private void insert(Whiskey whiskey, SQLConnection connection, Handler<AsyncResult<Whiskey>> next) {
		String sql = "insert into Whiskey (name, origin) values ?, ?";
		connection.updateWithParams(sql,
				new JsonArray().add(whiskey.getName()).add(whiskey.getOrigin()),
				(asyncHandler) -> {
					if (asyncHandler.failed()) {
						next.handle(Future.failedFuture(asyncHandler.cause()));
						return;
					}
					
					UpdateResult result = asyncHandler.result();

					// Build a new whiskey instance with the generated id.
					Whiskey instance = new Whiskey(result.getKeys().getInteger(0), whiskey.getName(), whiskey.getOrigin());
					next.handle(Future.succeededFuture(instance));
				});
		}*/
}