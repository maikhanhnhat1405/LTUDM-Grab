package com.delivery.server.event;

import com.delivery.common.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Hang doi su kien trong quy trinh (in-process).
 *
 * KIEN TRUC:
 *   publish(event)  ─→  BlockingQueue  ─→  N worker thread  ─→  listener1,2,3...
 *                            ^
 *                            └── OrderService return NGAY sau khi enqueue,
 *                                khong doi listener chay xong
 *
 * VI SAO CO:
 *   - Tach viec "phai xong ngay" (ghi DB, tra response cho client) khoi
 *     "co the cham" (day notification, ghi audit log, tinh thong ke).
 *   - Neu sau nay muon them AuditService thi chi can subscribe them, khong
 *     dong den OrderService.
 *   - Neu queue day thi biet ngay he thong dang qua tai - do la thong so
 *     rat huu ich cho monitoring.
 *
 * VI SAO CHI IN-PROCESS, KHONG DUNG KAFKA:
 *   Do an do vai chuc client, in-process la du. Interface EventBus rat mong nen
 *   sau nay thay bang Kafka/RabbitMQ chi phai sua noi file nay.
 *
 * Day chinh la o "Event Queue" trong so do kien truc goc.
 */
public class EventBus {

    private final Map<Class<? extends Event>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>(10_000);
    private final Thread[] workers;
    private volatile boolean running = true;

    public EventBus(int workerCount) {
        this.workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new Thread(this::consumeLoop, "event-worker-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
        }
        Log.info("EventBus", null, "Da khoi dong " + workerCount + " worker");
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void subscribe(Class<E> type, EventListener<E> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                 .add((EventListener<Event>) listener);
    }

    /**
     * Day ngay lap tuc, KHONG block. Neu queue day thi bo goi va log canh bao -
     * mat 1 event thong bao con hon lam ke goi bi treo.
     */
    public void publish(Event event) {
        if (!queue.offer(event)) {
            Log.warn("EventBus", event.traceId,
                    "Queue day (" + queue.size() + "), bo event " + event.getClass().getSimpleName());
        }
    }

    private void consumeLoop() {
        while (running) {
            try {
                Event event = queue.take();
                dispatch(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                Log.error("EventBus", null, "Loi dispatch event", e);
                // KHONG re-throw - mot event hong khong duoc lam chet worker
            }
        }
    }

    private void dispatch(Event event) {
        List<EventListener<?>> handlers = listeners.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) return;
        for (EventListener<?> h : handlers) {
            try {
                @SuppressWarnings("unchecked")
                EventListener<Event> typed = (EventListener<Event>) h;
                typed.onEvent(event);
            } catch (Exception e) {
                // Mot listener hong khong duoc anh huong cac listener khac cua cung event
                Log.error("EventBus", event.traceId,
                        "Listener " + h.getClass().getSimpleName() + " nem exception", e);
            }
        }
    }

    public int queueSize() { return queue.size(); }

    public void shutdown() {
        running = false;
        for (Thread w : workers) w.interrupt();
    }
}
