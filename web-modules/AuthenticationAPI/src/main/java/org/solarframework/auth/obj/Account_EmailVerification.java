package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.auth.obj.enums.EmailVerificationType;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "account_emailverification")
public class Account_EmailVerification extends DatabaseObject.ID_OBJ<Long, Account_EmailVerification> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "UserID")
    private transient Account_User U;

    @Column(name = "UserID", nullable = false)
    public Long userID;
    @Column(name = "Token", length = 128, nullable = false)
    public String token;
    @Enumerated(EnumType.STRING)
    @Column(name = "Type", length = 32, nullable = false)
    public EmailVerificationType type;
    @Column(name = "ExpiryDate", nullable = false)
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
        return retrieveEntityServiceFor(Account_EmailVerification.class).getWhere("Token = ?", token).orElse(null);
    }

    public Account_User getUser() {
        return U == null ? U = retrieveEntityServiceFor(Account_User.class).getById(userID).orElse(null) : U;
    }

    public static void ClearUnregisterUsers() {
        for (Account_EmailVerification vToken : retrieveEntityServiceFor(Account_EmailVerification.class).getAllWhere("Type = ? AND ExpiryDate < ?","REGISTRATION", Instant.now().toEpochMilli())) {
            vToken.getUser().Delete();
        }
    }
}
