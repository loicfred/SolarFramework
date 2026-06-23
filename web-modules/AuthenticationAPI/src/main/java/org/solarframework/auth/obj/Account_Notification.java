package org.solarframework.auth.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "account_notification")
public class Account_Notification extends DatabaseObject.ID_OBJ<Long, Account_Notification> {

    @Column(name = "UserID", nullable = false)
    public Long userID;
    @Column(name = "Title", length = 128, nullable = false)
    public String title;
    @Column(name = "Message", length = 512, nullable = false)
    public String message;
    @Column(name = "CreatedAt", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "Opened", nullable = false, columnDefinition = "TINYINT(1)")
    public boolean opened = false;

    public Account_Notification() {}
    public Account_Notification(Long userID, String title, String message) {
        this.ID = Instant.now().toEpochMilli();
        this.userID = userID;
        this.title = title;
        this.message = message;
        Write();
    }

    public Long getUserID() {
        return userID;
    }
    public void setUserID(Long userID) {
        this.userID = userID;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isOpened() {
        return opened;
    }
    public void setOpened(boolean read) {
        this.opened = read;
    }

    public static List<Account_Notification> ofUser(long userID, int limit) {
        return retrieveEntityServiceFor(Account_Notification.class).getAllWhere("UserID = ? ORDER BY ID DESC LIMIT ?;", userID, limit);
    }
}
