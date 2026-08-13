package com.delivery.server.event;

/**
 * Su kien nghiep vu (Order da tao, Driver da nhan, v.v.).
 *
 * Ban chat la mot lop marker rong - moi loai su kien ke thua rieng va mang
 * theo du lieu can thiet. EventBus khong biet ve noi dung, chi biet chuyen tiep.
 *
 * Vi sao lai co lop nay: OrderService khong nen tu di goi NotificationService,
 * AnalyticsService, AuditService... Neu goi truc tiep thi moi khi them mot loai
 * "phai lam khi don duoc tao" la phai sua OrderService. Publish 1 su kien roi
 * de cac listener tu dang ky la dung nguyen ly Open/Closed.
 */
public abstract class Event {
    public final long timestamp = System.currentTimeMillis();
    public String traceId;   // requestId cua request goc, de trace xuyen suot
}
