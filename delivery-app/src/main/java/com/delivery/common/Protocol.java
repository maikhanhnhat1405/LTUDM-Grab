package com.delivery.common;

import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TCP la STREAM chu khong phai message: doc 1 lan co the ra nua goi tin,
 * hoac dinh 2 goi vao nhau. Vi vay phai tu dong khung (framing).
 *
 * Khung tin:
 *   [1 byte frameType][4 byte body length (big-endian)][body...]
 *
 * frameType = 0 -> body la JSON UTF-8 (Message)
 * frameType = 1 -> body la binary (anh / voice - dung o Level 2)
 *
 * DataOutputStream.writeInt() da ghi big-endian nen khong can xu ly byte order.
 */
public final class Protocol {

    public static final byte FRAME_JSON   = 0;
    public static final byte FRAME_BINARY = 1;

    /** Chan goi tin di dang (hoac client pha hoai) lam server OOM. */
    public static final int MAX_FRAME_SIZE = 16 * 1024 * 1024; // 16MB

    private static final Gson GSON = new Gson();

    private Protocol() {}

    /**
     * Ghi 1 message. synchronized tren stream vi nhieu thread (vd: thread day
     * PUSH va thread tra RESPONSE) co the cung ghi vao 1 socket -> neu khong
     * khoa se ghi xen ke nhau va hong khung tin.
     */
    public static void writeMessage(DataOutputStream out, Message msg) throws IOException {
        byte[] body = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
        synchronized (out) {
            out.writeByte(FRAME_JSON);
            out.writeInt(body.length);
            out.write(body);
            out.flush();
        }
    }

    /** Danh cho Level 2 (gui anh/voice). */
    public static void writeBinary(DataOutputStream out, byte[] payload) throws IOException {
        synchronized (out) {
            out.writeByte(FRAME_BINARY);
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    /**
     * Doc dung 1 message. readFully() se block cho den khi du N byte
     * -> giai quyet triet de van de "nua goi tin".
     * Nem EOFException khi dau kia dong ket noi.
     */
    public static Message readMessage(DataInputStream in) throws IOException {
        byte frameType = in.readByte();
        int length = in.readInt();

        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Frame length khong hop le: " + length);
        }

        byte[] body = new byte[length];
        in.readFully(body);

        if (frameType != FRAME_JSON) {
            // Level 1 chua xu ly binary -> doc bo qua de stream khong bi lech
            return Message.push("BINARY_IGNORED");
        }
        return GSON.fromJson(new String(body, StandardCharsets.UTF_8), Message.class);
    }
}
