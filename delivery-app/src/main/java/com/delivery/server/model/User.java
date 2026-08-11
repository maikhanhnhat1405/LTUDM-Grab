package com.delivery.server.model;

import com.google.gson.JsonObject;

public class User {
    public long id;
    public String username;
    public String passwordHash;
    public String fullName;
    public String phone;
    public Role role;

    /** Chuyen sang JSON de gui cho client - KHONG bao gio kem passwordHash. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("username", username);
        o.addProperty("fullName", fullName);
        o.addProperty("phone", phone);
        o.addProperty("role", role.name());
        return o;
    }
}
