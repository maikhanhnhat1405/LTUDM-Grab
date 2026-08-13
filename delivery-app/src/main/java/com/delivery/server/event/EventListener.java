package com.delivery.server.event;

@FunctionalInterface
public interface EventListener<E extends Event> {
    void onEvent(E event);
}
