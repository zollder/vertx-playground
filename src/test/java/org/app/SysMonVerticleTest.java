package org.app;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.io.IOException;
import java.net.ServerSocket;

import org.app.model.Whiskey;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

@RunWith(VertxUnitRunner.class)
public class SysMonVerticleTest {

	private Vertx vertx;
	private Integer port;
	private static MongodProcess MONGO;
	private static int MONGO_PORT = 12345;

	@BeforeClass
	public static void initialize() throws IOException {
		MongodStarter starter = MongodStarter.getDefaultInstance();
		IMongodConfig mongodConfig = new MongodConfigBuilder()
			.version(Version.Main.PRODUCTION)
			.net(new Net(MONGO_PORT, Network.localhostIsIPv6()))
			.build();
		MongodExecutable mongodExecutable = starter.prepare(mongodConfig);
		MONGO = mongodExecutable.start();
	}

	@AfterClass
	public static void shutDown() {
		MONGO.stop();
	}

	@Before
	public void setUp(TestContext context) throws IOException {
		vertx = Vertx.vertx();

		// configure the verticle to listen on the randomly picked 'test' port.
		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		// create deployment options and set the _configuration_json object
		DeploymentOptions options = new DeploymentOptions()
			.setConfig(new JsonObject()
				.put("http.port", port)
				.put("db_name", "whiskies-test")
				.put("connection_string", "mongodb://localhost:" + MONGO_PORT)
			);

		vertx.deployVerticle(SysMonVerticle.class.getName(), options, context.asyncAssertSuccess());
	}

	@After
	public void tearDown(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}

	/**
	 * Creates an HTTP client, queries the application and verifies if the response contains the 'Hello' message.
	 * Calls `complete` on the async handler to declare this async (and here the test) done.
	 * Note: assert on 'context' object, not Junit assert. This allows managing the async aspect of the test the right way.
	 */
	@Test
	public void testApplication(TestContext context) {
		// get async handler to notify test framework when the test completes
		final Async asyncHandle = context.async();

		// emit request and test result
		vertx.createHttpClient().getNow(port, "localhost", "/", response -> {
					response.handler(body -> {
						context.assertTrue(body.toString().contains("Hello"));
						asyncHandle.complete();	// notifier
					});
				});
	}

	@Test
	public void testIndexPageServed(TestContext context) {
		Async async = context.async();
		vertx.createHttpClient().getNow(port, "localhost", "/assets/index.html", response -> {
			context.assertEquals(response.statusCode(), 200);
			System.out.println(response.headers().get("content-type"));
				context.assertEquals("text/html;charset=UTF-8", response.headers().get("content-type"));
			response.bodyHandler(body -> {
				context.assertTrue(body.toString().contains("<title>My Whisky Collection</title>"));
				async.complete();
			});
		});
	}

	@Test
	public void testCanAddListItem(TestContext context) {
		Async async = context.async();
		final String product = Json.encodePrettily(new Whiskey("Jameson", "Ireland"));
		final String length = Integer.toString(product.length());
		vertx.createHttpClient().post(port, "localhost", "/api/whiskies")
			.putHeader("content-type", "application/json")
			.putHeader("content-length", length)
			.handler(response -> {
				context.assertEquals(response.statusCode(), 201);
				context.assertEquals(response.headers().get("content-type"), "application/json; charset=utf-8");
				response.bodyHandler(body -> {
					final Whiskey whiskey = Json.decodeValue(body.toString(), Whiskey.class);
					context.assertEquals(whiskey.getName(), "Jameson");
					context.assertEquals(whiskey.getOrigin(), "Ireland");
					context.assertNotNull(whiskey.getId());
					async.complete();
				});
			})
			.write(product)
			.end();
	}
}