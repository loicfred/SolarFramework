package org.solarframework.db.test.obj;

import jakarta.persistence.*;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "user")
public class User extends DatabaseObject.ID_RECORD_OBJ<Long, User> {

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Order> orders;

    @Column(name = "Name")
    private String name;
    @Column(name = "Email")
    private String email;

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
}
