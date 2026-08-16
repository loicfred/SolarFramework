package org.solarframework.db.test.obj;

import jakarta.persistence.*;
import org.solarframework.db.api.Lazy;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Entity
@Table(name = "user")
public class User extends DatabaseObject.ID_RECORD_OBJ<Long, User> {

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<Order> orders = new ArrayList<>();

    // Same association declared as a Set: PostLoad must honour the field's own type, not hand every
    // inverse collection a List proxy. See LazyMappedCollection.
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    public Set<Order> orderSet = new LinkedHashSet<>();

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

    public byte[] getAvatar() {
        return Lazy.unloaded(avatar) ? avatar = refetchAttribute("Avatar", byte[].class) : avatar;
    }
    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

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

    public List<Order> getOrders() {
        return orders;
    }

    public Set<Order> getOrderSet() {
        return orderSet;
    }

    public Order addOrder(String item, int amount) {
        Order o = new Order(this, item, amount);
        getOrders().add(o);
        return o;
    }
}
