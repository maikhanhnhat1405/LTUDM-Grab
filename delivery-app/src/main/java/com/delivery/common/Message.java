package com.delivery.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Envelope chung cho moi goi tin JSON.
 * {"type":"LOGIN","requestId":"uuid","timestamp":1754900000,"data":{...}}
 *
 * requestId la chia khoa cua kien truc bat dong bo:
 * client gui request kem id -> server tra response mang DUNG id do
 * -> client biet response nay ung voi request nao (xem ClientConnection).
 */
public class Message {

    private String type;
    private String requestId;   // null neu la PUSH tu server
    private long timestamp;
    private JsonObject data = new JsonObject();

    public Message() {}         // Gson can constructor rong

    private Message(String type, String requestId) {
        this.type = type;
        this.requestId = requestId;
        this.timestamp = System.currentTimeMillis();
    }

    public static Message request(String type) {
        return new Message(type, UUID.randomUUID().toString());
    }

    /** Response phai giu nguyen requestId cua request goc. */
    public static Message response(String type, String requestId) {
        return new Message(type, requestId);
    }

    public static Message ok(String requestId) {
        return new Message(MessageType.OK, requestId);
    }

    public static Message error(String requestId, String code, String message) {
        return new Message(MessageType.ERROR, requestId)
                .put("code", code)
                .put("message", message);
    }

    /** Server chu dong day xuong client, khong gan voi request nao. */
    public static Message push(String type) {
        return new Message(type, null);
    }

    public String getType()      { return type; }
    public String getRequestId() { return requestId; }
    public long getTimestamp()   { return timestamp; }

    public JsonObject getData() {
        if (data == null) data = new JsonObject();
        return data;
    }

    public boolean isError() { return MessageType.ERROR.equals(type); }

    // ---------- setter dang chuoi (fluent) ----------
    public Message put(String key, String value)      { getData().addProperty(key, value); return this; }
    public Message put(String key, Number value)      { getData().addProperty(key, value); return this; }
    public Message put(String key, Boolean value)     { getData().addProperty(key, value); return this; }
    public Message put(String key, JsonElement value) { getData().add(key, value);         return this; }

    // ---------- getter an toan (khong nem exception khi thieu field) ----------
    public String str(String key) {
        JsonElement e = getData().get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    public long lng(String key) {
        JsonElement e = getData().get(key);
        return (e == null || e.isJsonNull()) ? 0L : e.getAsLong();
    }

    public double dbl(String key) {
        JsonElement e = getData().get(key);
        return (e == null || e.isJsonNull()) ? 0d : e.getAsDouble();
    }

    public boolean bool(String key) {
        JsonElement e = getData().get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    @Override
    public String toString() {
        return type + "[" + requestId + "] " + getData();
    }
}
