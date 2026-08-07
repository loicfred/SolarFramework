package org.solarframework.mu;

import jakarta.persistence.*;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Entity
@Table(name = "user")
public class User extends DatabaseObject.ID_RECORD_OBJ<Long, User> {

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Order> orders;

    @Column(name = "Name")
    private String name;
    @Column(name = "Email")
    private String email;
    @Column(name = "Avatar", columnDefinition = "MEDIUMBLOB")
    private byte[] avatar;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public byte[] getAvatar() {
        return avatar;
    }
    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    protected User() {}
    public User(Long id, String name, String email) {
        this.ID = id;
        this.name = name;
        this.email = email;
    }
    public User(String name, String email) {
        this(Instant.now().toEpochMilli(), name, email);
    }

    public List<Order> getOrders() {
        return orders == null ? orders = new ArrayList<>() : orders;
    }

    public Order addOrder(String item, int amount) {
        Order o = new Order(this, item, amount);
        getOrders().add(o);
        return o;
    }
}
