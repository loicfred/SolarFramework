package org.solarframework.db.test.obj;

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

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<Order> orders;

    @Column(name = "Name")
    private String name;
    @Column(name = "Email")
    private String email;
    @Column(name = "Avatar", columnDefinition = "MEDIUMBLOB")
    private byte[] avatar = Lazy.UNLOADED;

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

    /** Blob column: reads leave it out, so the first caller that wants the bytes pays for them - and only that one. */
    public byte[] getAvatar() {
        return Lazy.unloaded(avatar) ? avatar = refetchAttribute("Avatar", byte[].class) : avatar;
    }
    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }
    /** Test hook: whether the bytes are in memory, which is the whole point of the column being lazy. */
    public boolean isAvatarLoaded() {
        return !Lazy.unloaded(avatar);
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

    /** Association: a hand-built user holds no ORM collection, so the first reader fetches it through the framework. */
    public List<Order> getOrders() {
        return orders == null ? orders = new ArrayList<>(SolarDBManager.getAllWhere(Order.class, "UserID = ?", getID())) : orders;
    }

    /** Attaches a child through the getter, so the list is whole before it grows - an unsaved user just loads nothing. */
    public Order addOrder(String item, int amount) {
        Order o = new Order(this, item, amount);
        getOrders().add(o);
        return o;
    }
}
