package com.delivery.server.model;

/**
 * State machine cua don hang. Server PHAI kiem tra transition truoc khi update,
 * neu khong client sua tay goi tin la lam hong du lieu.
 *
 * PENDING -> ACCEPTED -> PICKED_UP -> DELIVERING -> COMPLETED
 *    |          |            |
 *    +----------+------------+--> CANCELLED
 */
public enum OrderStatus {
    PENDING, ACCEPTED, PICKED_UP, DELIVERING, COMPLETED, CANCELLED;

    public boolean canGoTo(OrderStatus next) {
        switch (this) {
            case PENDING:    return next == ACCEPTED   || next == CANCELLED;
            case ACCEPTED:   return next == PICKED_UP  || next == CANCELLED;
            case PICKED_UP:  return next == DELIVERING || next == CANCELLED;
            case DELIVERING: return next == COMPLETED;
            default:         return false;   // COMPLETED / CANCELLED la trang thai cuoi
        }
    }

    public static OrderStatus parse(String s) {
        try { return valueOf(s); } catch (Exception e) { return null; }
    }
}
