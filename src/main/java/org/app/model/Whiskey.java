package org.app.model;

import io.vertx.core.json.JsonObject;

public class Whiskey {

	private int id;
	private String name;
	private String origin;

	public Whiskey() {
		this.id = -1;
	}

	public Whiskey(JsonObject json) {
		this.name = json.getString("NAME");
		this.origin = json.getString("ORIGIN");
		this.id = json.getInteger("ID");
	}

	public Whiskey(String name, String origin) {
		this.name = name;
		this.origin = origin;
		this.id = -1;
	}

	public Whiskey(int id, String name, String origin) {
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

	public int getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}
}