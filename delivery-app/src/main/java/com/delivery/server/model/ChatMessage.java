package com.delivery.server.model;

import com.google.gson.JsonObject;

public class ChatMessage {
    public long id;
    public long orderId;
    public long senderId;
    public String senderName;
    public String content;
    public String type = "TEXT";
    public String createdAt;

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("orderId", orderId);
        o.addProperty("senderId", senderId);
        o.addProperty("senderName", senderName);
        o.addProperty("content", content);
        o.addProperty("type", type);
        o.addProperty("createdAt", createdAt);
        return o;
    }
}
