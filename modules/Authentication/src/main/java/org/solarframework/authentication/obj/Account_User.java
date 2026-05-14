package org.solarframework.authentication.obj;

import jakarta.persistence.*;
import org.solarframework.authentication.obj.enums.Gender;
import org.solarframework.authentication.obj.enums.UserStatus;
import org.solarframework.core.lang.Nationalities;
import org.solarframework.db.api.DatabaseObject;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@Entity
@Table(name = "account_user")
public class Account_User extends DatabaseObject.ID_OBJ<Long, Account_User> {

    @Column(name = "Username", nullable = false, length = 50)
    private String username;
    @Column(name = "Email", nullable = false)
    private String email;
    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;
    @Column(name = "FirstName", length = 100)
    private String firstName;
    @Column(name = "LastName", length = 100)
    private String lastName;
    @Column(name = "DateOfBirth")
    private Instant dateOfBirth;
    @Column(name = "DisplayName", length = 150)
    private String displayName;
    @Column(name = "Avatar")
    private byte[] avatar;
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;
    @Column(name = "EmailVerified", nullable = false)
    private Boolean emailVerified = false;
    @Column(name = "PhoneNumber", length = 20)
    private String phoneNumber;
    @Column(name = "PhoneVerified")
    private Boolean phoneVerified = false;
    @Column(name = "FailedLoginAttempts")
    private Integer failedLoginAttempts = 0;
    @Column(name = "LastLoginAt")
    private Instant lastLoginAt;
    @Column(name = "LastSeenAt")
    private Instant lastSeenAt;
    @Column(name = "LoginCount")
    private Integer loginCount = 0;
    @Column(name = "AccountLockedUntil")
    private Instant accountLockedUntil;
    @Enumerated(EnumType.STRING)
    @Column(name = "Nationality", length = 20)
    private Nationalities nationality = Nationalities.International;
    @Enumerated(EnumType.STRING)
    @Column(name = "Gender", length = 6)
    private Gender gender = Gender.OTHER;
    @Column(name = "Timezone", length = 50)
    private String timezone = "UTC";
    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "UpdatedAt", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "DeletedAt")
    private Instant deletedAt;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Instant getDateOfBirth() {
        return dateOfBirth;
    }
    public void setDateOfBirth(Instant dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public byte[] getAvatar() {
        return avatar == null ? avatar = refetchAttribute("Avatar", byte[].class) : avatar;
    }
    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
        UpdateOnly("Avatar");
    }

    public UserStatus getStatus() {
        return status;
    }
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Boolean isEmailVerified() {
        return emailVerified;
    }
    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean isPhoneVerified() {
        return phoneVerified;
    }
    public void setPhoneVerified(Boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Integer getLoginCount() {
        return loginCount;
    }
    public void setLoginCount(Integer loginCount) {
        this.loginCount = loginCount;
    }

    public Instant getAccountLockedUntil() {
        return accountLockedUntil;
    }
    public void setAccountLockedUntil(Instant accountLockedUntil) {
        this.accountLockedUntil = accountLockedUntil;
    }

    public Nationalities getNationality() {
        return nationality;
    }
    public void setNationality(Nationalities language) {
        this.nationality = language;
    }

    public Gender getGender() {
        return gender;
    }
    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getTimezone() {
        return timezone;
    }
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Account_User() {
        this.ID = Instant.now().toEpochMilli();
    }


    public static Account_User getByEmail(String email) {
        return SolarDBManager.getWhere(Account_User.class, "Email = ?", email).orElse(null);
    }
    public static Account_User getByPhone(String phone) {
        return SolarDBManager.getWhere(Account_User.class, "Phone = ?", phone).orElse(null);
    }
    public static Account_User getByAuthentication(Principal principal) {
        if (principal == null) return null;
        return SolarDBManager.getWhere(Account_User.class, "Email = ?", principal.getName()).orElse(null);
    }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(LocalDate.ofInstant(dateOfBirth, ZoneId.systemDefault()), LocalDate.now()).getYears();
    }
}