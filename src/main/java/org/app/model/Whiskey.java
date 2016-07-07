package org.app.model;

import io.vertx.core.json.JsonObject;

public class Whiskey {

	private String id;
	private String name;
	private String origin;

	public Whiskey() {
		this.id = "";
	}

	public Whiskey(JsonObject json) {
		this.name = json.getString("name");
		this.origin = json.getString("origin");
		this.id = json.getString("_id");
	}

	public Whiskey(String name, String origin) {
		this.name = name;
		this.origin = origin;
		this.id = "";
	}

	public Whiskey(String id, String name, String origin) {
		this.id = id;
		this.name = name;
		this.origin = origin;
	}

	public String getName() {
		return name;
	}

	public String getOrigin() {
		return origin;
	}

	public String getId() {
		return id;
	}

	public Whiskey setname(String name) {
		this.name = name;
		return this;
	}

	public Whiskey setOrigin(String origin) {
		this.origin = origin;
		return this;
	}

	public Whiskey setId(String id) {
		this.id = id;
		return this;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject().put("name", name)
			.put("origin", origin);
		if (id != null && !id.isEmpty()) {
			json.put("_id", id);
		}
		return json;
	}
}