package org.solarframework.auth.obj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.solarframework.auth.obj.enums.EmailVerificationType;
import org.solarframework.auth.obj.enums.UserStatus;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "account_emailverification")
public class Account_EmailVerification extends DatabaseObject.ID_OBJ<Long, Account_EmailVerification> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", referencedColumnName = "ID", nullable = false, insertable = false, updatable = false)
    private Account_User user;

    @Column(name = "UserID", nullable = false)
    public Long userID;
    @Column(name = "Token", length = 128, nullable = false)
    public String token;

    @Convert(converter = TypeConverter.class)
    @Column(name = "Type", length = 32, nullable = false)
    public EmailVerificationType type;
    @Converter
    public static class TypeConverter implements AttributeConverter<EmailVerificationType, String> {
        @Override
        public String convertToDatabaseColumn(EmailVerificationType type) {
            return type == null ? null : type.name();
        }
        @Override
        public EmailVerificationType convertToEntityAttribute(String name) {
            return name == null ? null : EmailVerificationType.valueOf(name);
        }
    }

    @Column(name = "ExpiryDate", nullable = false)
    public Long expiryDate = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();

    protected Account_EmailVerification() {}
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
        return user == null ? user = retrieveEntityServiceFor(Account_User.class).getById(userID).orElse(null) : user;
    }

    public static void ClearUnregisterUsers() {
        for (Account_EmailVerification vToken : retrieveEntityServiceFor(Account_EmailVerification.class).getAllWhere("Type = ? AND ExpiryDate < ?","REGISTRATION", Instant.now().toEpochMilli())) {
            vToken.getUser().Delete();
        }
    }
}
