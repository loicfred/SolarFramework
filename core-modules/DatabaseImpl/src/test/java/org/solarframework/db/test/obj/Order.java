package org.solarframework.db.test.obj;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;

@Entity
@Table(name = "orders")
@SQLRestriction("DeletedAt IS NULL")
public class Order extends DatabaseObject.ID_RECORD_OBJ<Long, Order> {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(referencedColumnName = "ID", name = "UserID", nullable = false, insertable = false, updatable = false)
    public User user;

    @Column(name = "Item")
    private String item;
    @Column(name = "UserID")
    private Long userId;
    @Column(name = "Amount", nullable = false)
    private Integer amount = 0;
    @Column(name = "Active", nullable = false)
    private Boolean active = false;


    @Convert(converter = StatusConverter.class)
    @Column(name = "Status", nullable = false)
    private Status status = Status.PENDING;
    @Converter
    public static class StatusConverter implements AttributeConverter<Status, String> {
        public String convertToDatabaseColumn(Status type) {
            return type == null ? null : type.name();
        }
        public Status convertToEntityAttribute(String name) {return name == null ? null : Status.valueOf(name);}
    }


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

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }

    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }

    protected Order() {}
    public Order(Long id, User u, String item) {
        this.ID = id;
        this.item = item;
        this.userId = u.getID();
    }
    public Order(Long id, User u, String item, Integer amount) {
        this.ID = id;
        this.item = item;
        this.userId = u.getID();
        this.amount = amount;
    }
    public Order(User u, String item, Integer amount) {
        this(Instant.now().toEpochMilli(), u, item, amount);
    }

    public User getUser() {
        return user;
    }
}
