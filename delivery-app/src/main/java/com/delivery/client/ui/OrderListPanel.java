package com.delivery.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Danh sách đơn hàng dạng card/button.
 *
 * Mỗi đơn là một OrderCard - bấm vào sẽ gọi onSelect callback.
 * Load/upsert tương tự OrderTableModel, nhưng UI dạng card chứ không phải table.
 */
public class OrderListPanel extends JPanel {

    private final JPanel listPanel = new JPanel();
    private final List<OrderCard> cards = new ArrayList<>();
    private final Consumer<JsonObject> onSelect;
    private OrderCard selected;
    private final List<ListSelectionListener> listeners = new ArrayList<>();

    public OrderListPanel(Consumer<JsonObject> onSelect) {
        super(new BorderLayout());
        this.onSelect = onSelect;

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = Theme.scroll(listPanel);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    public void setAll(JsonArray arr) {
        cards.clear();
        selected = null;
        listPanel.removeAll();

        if (arr == null || arr.size() == 0) {
            JLabel empty = Theme.muted("Không có đơn hàng nào");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel p = Theme.transparent(new BorderLayout());
            p.add(empty, BorderLayout.CENTER);
            listPanel.add(p);
        } else {
            for (JsonElement el : arr) {
                addCard(el.getAsJsonObject());
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    public void upsert(JsonObject order) {
        long id = order.get("id").getAsLong();
        for (OrderCard c : cards) {
            if (c.getOrderId() == id) {
                c.update(order);
                listPanel.revalidate();
                listPanel.repaint();
                return;
            }
        }
        // Chèn đơn mới vào đầu
        listPanel.remove(0);  // xoá "không có đơn" nếu có
        OrderCard card = new OrderCard(order, this::selectCard);
        cards.add(0, card);
        listPanel.add(card, 0);
        listPanel.revalidate();
        listPanel.repaint();
    }

    public void removeById(long id) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).getOrderId() == id) {
                cards.remove(i);
                listPanel.remove(i);
                if (selected != null && selected.getOrderId() == id) selected = null;
                if (cards.isEmpty()) {
                    JLabel empty = Theme.muted("Không có đơn hàng nào");
                    empty.setHorizontalAlignment(SwingConstants.CENTER);
                    listPanel.add(empty);
                }
                listPanel.revalidate();
                listPanel.repaint();
                return;
            }
        }
    }

    public JsonObject getSelected() {
        return selected == null ? null : selected.order;
    }

    public JsonObject getById(long id) {
        for (OrderCard c : cards) {
            if (c.getOrderId() == id) return c.order;
        }
        return null;
    }

    public void addListSelectionListener(ListSelectionListener l) {
        listeners.add(l);
    }

    private void notifyListeners() {
        ListSelectionEvent e = new ListSelectionEvent(this, 0, cards.size() - 1, false);
        for (ListSelectionListener l : listeners) l.valueChanged(e);
    }

    private void addCard(JsonObject order) {
        OrderCard card = new OrderCard(order, this::selectCard);
        cards.add(card);
        listPanel.add(card);
    }

    private void selectCard(OrderCard card) {
        if (selected != null) selected.setSelected(false);
        selected = card;
        card.setSelected(true);
        onSelect.accept(card.order);
        notifyListeners();
    }

    // ------------------------------------------------------------------
    // Một card hiện một đơn dạng compact
    // ------------------------------------------------------------------
    private static class OrderCard extends JPanel {

        final JsonObject order;
        private boolean highlighted;

        OrderCard(JsonObject order, Consumer<OrderCard> onSelect) {
            super(new BorderLayout(10, 0));
            this.order = order;
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            String status = order.get("status").getAsString();
            long id = order.get("id").getAsLong();
            String from = order.get("pickupAddr").getAsString();
            String to = order.get("dropoffAddr").getAsString();
            String price = Theme.money(order.get("price").getAsDouble());

            // Cột trái: ID + status
            JPanel left = Theme.transparent(new GridLayout(2, 1, 0, 2));
            JLabel idLabel = new JLabel("#" + id);
            idLabel.setFont(Theme.FONT_BOLD);
            idLabel.setForeground(Theme.TEXT);

            JLabel statusLabel = new JLabel(Theme.statusLabel(status));
            statusLabel.setFont(Theme.SMALL_BOLD);
            statusLabel.setForeground(Theme.statusColor(status));

            left.add(idLabel);
            left.add(statusLabel);
            left.setPreferredSize(new Dimension(70, 0));

            // Cột giữa: pickup -> dropoff
            JPanel center = Theme.transparent(new GridLayout(2, 1, 0, 2));
            JLabel fromLabel = new JLabel(truncate(from, 20));
            fromLabel.setFont(Theme.SMALL);
            fromLabel.setForeground(Theme.TEXT);

            JLabel toLabel = new JLabel("→ " + truncate(to, 20));
            toLabel.setFont(Theme.SMALL);
            toLabel.setForeground(Theme.MUTED);

            center.add(fromLabel);
            center.add(toLabel);

            // Cột phải: giá
            JLabel priceLabel = new JLabel(price);
            priceLabel.setFont(Theme.FONT_BOLD);
            priceLabel.setForeground(Theme.BRAND);
            priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            add(left, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
            add(priceLabel, BorderLayout.EAST);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    onSelect.accept(OrderCard.this);
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    setBackground(Theme.BRAND_SOFT);
                    setOpaque(true);
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (!highlighted) {
                        setOpaque(false);
                        repaint();
                    }
                }
            });
        }

        long getOrderId() { return order.get("id").getAsLong(); }

        void update(JsonObject newOrder) {
            order.entrySet().forEach(e -> {
                if (newOrder.has(e.getKey())) {
                    order.add(e.getKey(), newOrder.get(e.getKey()));
                }
            });
            repaint();
        }

        void setSelected(boolean sel) {
            highlighted = sel;
            if (sel) {
                setBackground(new Color(0xE4F6EC));
                setOpaque(true);
            } else {
                setOpaque(false);
            }
            repaint();
        }

        private static String truncate(String s, int maxLen) {
            return s.length() > maxLen ? s.substring(0, maxLen - 1) + "..." : s;
        }
    }
}
