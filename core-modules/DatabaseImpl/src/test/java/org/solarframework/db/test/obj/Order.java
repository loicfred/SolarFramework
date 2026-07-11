package org.solarframework.db.test.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order extends DatabaseObject.ID_RECORD_OBJ<Long, Order> {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "UserID", nullable = false, insertable = false, updatable = false)
    public User user;

    @Column(name = "Item")
    private String item;
    @Column(name = "UserID")
    private Long userId;
    @Column(name = "Amount")
    private Integer amount;

    public String getItem() {
        return item;
    }
    public void setItem(String item) {
        this.item = item;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getAmount() {
        return amount;
    }
    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    protected Order() {}
    public Order(Long id, User u, String item, Integer amount) {
        this.ID = id;
        this.item = item;
        this.userId = u.getID();
        this.amount = amount;
    }
    public Order(User u, String item, Integer amount) {
        this(Instant.now().toEpochMilli(), u, item, amount);
    }
}
