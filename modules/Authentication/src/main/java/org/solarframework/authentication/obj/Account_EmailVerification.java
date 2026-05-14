package org.solarframework.authentication.obj;

import jakarta.persistence.*;
import org.solarframework.authentication.obj.enums.EmailVerificationType;
import org.solarframework.db.api.DatabaseObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Entity
@Table(name = "email_verification")
public class Account_EmailVerification extends DatabaseObject.ID_OBJ<Long, Account_EmailVerification> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "UserID")
    private transient Account_User U;

    @Column(name = "UserID", nullable = false)
    public Long userID;
    @Column(name = "Token", length = 64, nullable = false)
    public String token;
    @Enumerated(EnumType.STRING)
    @Column(name = "Type", length = 20, nullable = false)
    public EmailVerificationType type;
    @Column(name = "ExpirtyDate", nullable = false)
    public Long expiryDate = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();

    public Account_EmailVerification() {}
    public Account_EmailVerification(Account_User authAccountUser, String token, EmailVerificationType type) {
        this.ID = Instant.now().toEpochMilli();
        this.userID = authAccountUser.ID;
        this.token = token;
        this.type = type;
        Write();
    }

    public Long getUserID() {
        return userID;
    }
    public void setUserID(Long userID) {
        this.userID = userID;
    }

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }

    public EmailVerificationType getType() {
        return type;
    }
    public void setType(EmailVerificationType type) {
        this.type = type;
    }

    public Long getExpiryDate() {
        return expiryDate;
    }
    public void setExpiryDate(Long expiryDate) {
        this.expiryDate = expiryDate;
    }


    public static Account_EmailVerification getByToken(String token) {
        return SolarDBManager.getWhere(Account_EmailVerification.class, "Token = ?", token).orElse(null);
    }

    public Account_User getUser() {
        return U == null ? U = SolarDBManager.getById(Account_User.class, userID).orElse(null) : U;
    }

    public static void ClearUnregisterUsers() {
        for (Account_EmailVerification vToken : SolarDBManager.getAllWhere(Account_EmailVerification.class, "Type = ? AND ExpiryDate < ?","REGISTRATION", Instant.now().toEpochMilli())) {
            vToken.getUser().Delete();
        }
    }
}
