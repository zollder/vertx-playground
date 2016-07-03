package org.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
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

import java.util.List;
import java.util.stream.Collectors;

import org.app.model.Whiskey;

public class SysMonVerticle extends AbstractVerticle {

	private JDBCClient jdbc;

	@Override
	public void start(Future<Void> future) {
		// create an instance of JDBC client
		//		jdbc = JDBCClient.createShared(vertx, config(), "My-Whisky-Collection");
		JsonObject jdbcConfig = new JsonObject()
			.put("url", "jdbc:hsqldb:mem:test?shutdown=true")
			.put("driver_class", "org.hsqldb.jdbcDriver")
			.put("max_pool_size", 30);
		jdbc = JDBCClient.createShared(vertx, jdbcConfig);

		// starts JDBC connection, creates data, and starts HTTP server
		startBackend(connection -> createData(connection,
				(nothing) -> startWebApp((http) -> completeStartup(http, future)), future),
				future);
	}

	@Override
	public void stop() throws Exception {
		// close JDBC client
		this.jdbc.close();
	}

	/**
	 * Retrieves an SQLConnection and calls the next action step.
	 * @param next - next step
	 * @param future - completion future passed by vert.x to report success or failure
	 */
	private void startBackend(Handler<AsyncResult<SQLConnection>> nextAction, Future<Void> future) {
		jdbc.getConnection(connection -> {
			if (connection.failed()) {
				future.fail(connection.cause());
			} else {
				nextAction.handle(Future.succeededFuture(connection.result()));
			}
		});
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
		jdbc.getConnection(requestHandler -> {
			// get an SQL connection, issue a query and write the response once the result is retrieved
			SQLConnection connection = requestHandler.result();
			connection.query("select * from Whiskey", result -> {
				List<Whiskey> products = result.result().getRows().stream()
						.map(Whiskey::new)
						.collect(Collectors.toList());
				routingContext.response()
					.putHeader("content-type", "application/json; charset=utf-8")
					.end(Json.encodePrettily(products));
				connection.close();
			});
		});
	}

	private void addOne(RoutingContext routingContext) {
		jdbc.getConnection(asyncRequest -> {
			final Whiskey product = Json.decodeValue(routingContext.getBodyAsString(), Whiskey.class);
			SQLConnection connection = asyncRequest.result();
			insert(product, connection, (nextAction) -> routingContext.response()
					.setStatusCode(201)
					.putHeader("content-type", "application-json; charset=utf-8")
					.end(Json.encodePrettily(nextAction.result()))
			);
		});


	}

	private void getOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response().setStatusCode(400).end();
		} else {
			jdbc.getConnection(asyncRequest -> {
				// Read the request's content and create a product instance.
				SQLConnection connection = asyncRequest.result();
				select(id, connection, resultHandler -> {
					if (resultHandler.succeeded()) {
						routingContext.response()
							.setStatusCode(200)
							.putHeader("content-type", "application/json; charset=utf-8")
							.end(Json.encodePrettily(resultHandler.result()));
					} else {
						routingContext.response()
							.setStatusCode(400)
							.end();
					}
				});

			});
		}
	}

	private void updateOne(RoutingContext routingContext) {
		String id = routingContext.request()
			.getParam("id");
		JsonObject json = routingContext.getBodyAsJson();
		if (id == null || json == null) {
			routingContext.response()
				.setStatusCode(400)
				.end();
		} else {
			jdbc.getConnection(asyncRequest -> update(id, json, asyncRequest.result(), product -> {
				if (product.failed()) {
					routingContext.response()
						.setStatusCode(404)
						.end();
				} else {
					routingContext.response()
						.putHeader("content-type", "application/json; charset=utf-8")
						.end(Json.encodePrettily(product.result()));
				}
			}));
		}
	}

	private void deleteOne(RoutingContext routingContext) {
		String id = routingContext.request().getParam("id");
		if (id == null) {
			routingContext.response()
				.setStatusCode(400)
				.end();
		} else {
			jdbc.getConnection(asyncRequest -> {
				SQLConnection connection = asyncRequest.result();
				String sql = "delete from Whiskey where id='" + id + "'";
				connection.execute(sql, result -> {
					routingContext.response()
						.setStatusCode(204)
						.end();
					connection.close();
				});
			});
		}
	}

	private void createData(AsyncResult<SQLConnection> result, Handler<AsyncResult<Void>> next, Future<Void> future) {
		if (result.failed()) {
			future.fail(result.cause());
		} else {
			SQLConnection connection = result.result();
			String createTableStatement = "create table if not exists Whiskey (id INTEGER IDENTITY, name varchar(100), origin varchar(100))";
			connection.execute(createTableStatement, asyncHandler -> {
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
						insert(new Whiskey("Bowmore 15 Years Laimrig", "Scotland, Islay"),
								connection,
								(v) -> insert(new Whiskey("Talisker 57° North", "Scotland, Island"),
										connection,
										(r) -> {
											next.handle(Future.<Void>succeededFuture());
											connection.close();
										}));
					} else {
						next.handle(Future.<Void>succeededFuture());
						connection.close();
					}
				});
			});
		}
	}

	private void insert(Whiskey product, SQLConnection connection, Handler<AsyncResult<Whiskey>> next) {
		String sql = "insert into Whiskey (name, origin) values ?, ?";
		connection.updateWithParams(sql,
				new JsonArray().add(product.getName()).add(product.getOrigin()),
				(asyncHandler) -> {
					if (asyncHandler.failed()) {
						next.handle(Future.failedFuture(asyncHandler.cause()));
						return;
					}

					UpdateResult result = asyncHandler.result();

					// Build a new product instance with the generated id.
					Whiskey instance = new Whiskey(result.getKeys().getInteger(0), product.getName(), product.getOrigin());
					next.handle(Future.succeededFuture(instance));
				}
		);
	}

	private void select(String id, SQLConnection connection, Handler<AsyncResult<Whiskey>> resultHandler) {
		String sql = "select * from Whiskey where id=?";
		connection.queryWithParams(sql,
				new JsonArray().add(id),
				asyncResult -> {
					if (asyncResult.failed()) {
						resultHandler.handle(Future.failedFuture("Product not found"));
					} else {
						if (asyncResult.result().getNumRows() >= 1) {
							resultHandler.handle(Future.succeededFuture(new Whiskey(asyncResult.result().getRows().get(0))));
						} else {
							resultHandler.handle(Future.failedFuture("Product not found"));
						}
					}
				}
		);
	}

	private void update(String id, JsonObject content, SQLConnection connection, Handler<AsyncResult<Whiskey>> resultHandler) {
		String sql = "update Whiskey set name=?, origin=? where id=?";
		connection.updateWithParams(sql,
				new JsonArray().add(content.getString("name")).add(content.getString("origin")).add(id),
				update -> {
					if (update.failed()) {
						resultHandler.handle(Future.failedFuture("Cannot update product"));
						return;
					}
					if (update.result().getUpdated() == 0) {
						resultHandler.handle(Future.failedFuture("Product not found"));
						return;
					}
					Whiskey product = new Whiskey(Integer.valueOf(id), content.getString("name"), content.getString("origin"));
					resultHandler.handle(Future.succeededFuture(product));
				}
		);
	}
}