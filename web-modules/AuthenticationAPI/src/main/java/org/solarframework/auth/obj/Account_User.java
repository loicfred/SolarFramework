package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.auth.obj.enums.AccountProvider;
import org.solarframework.auth.obj.enums.Gender;
import org.solarframework.auth.obj.enums.UserStatus;
import org.solarframework.lang.Nationalities;
import org.solarframework.db.spring.DatabaseObject;

import java.security.Principal;
import java.time.*;

@Entity
@Table(name = "account_user")
public class Account_User extends DatabaseObject.ID_OBJ_RECORD<Long, Account_User> {
    @ManyToOne
    @JoinColumn(referencedColumnName = "ID", name = "RoleID")
    private transient Account_Role role;

    @Column(name = "Username", nullable = false, length = 50)
    private String username;
    @Column(name = "Email", length = 128, nullable = false)
    private String email;
    @Column(name = "PasswordHash", length = 512, nullable = false)
    private String passwordHash;
    @Column(name = "RoleID")
    private Long roleId = 0L;
    @Column(name = "FirstName", length = 100)
    private String firstName;
    @Column(name = "LastName", length = 100)
    private String lastName;
    @Column(name = "DateOfBirth")
    private LocalDate dateOfBirth;
    @Column(name = "DisplayName", length = 150)
    private String displayName;
    @Column(name = "Avatar")
    private byte[] avatar;
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;
    @Column(name = "EmailVerified", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean emailVerified = false;
    @Column(name = "PhoneNumber", length = 20)
    private String phoneNumber;
    @Column(name = "Country", length = 32)
    private String Country;
    @Column(name = "City", length = 64)
    private String City;
    @Column(name = "Address", length = 128)
    private String Address;
    @Column(name = "PhoneVerified", columnDefinition = "TINYINT(1)")
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
    private LocalDateTime accountLockedUntil;
    @Enumerated(EnumType.STRING)
    @Column(name = "Nationality", length = 20)
    private Nationalities nationality = Nationalities.International;
    @Enumerated(EnumType.STRING)
    @Column(name = "Gender", length = 6)
    private Gender gender = Gender.OTHER;
    @Column(name = "Timezone", length = 50)
    private String timezone = "UTC";
    @Enumerated(EnumType.STRING)
    @Column(name = "AccountProvider", length = 32)
    private AccountProvider accountProvider;

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

    public Long getRoleId() {
        return roleId;
    }
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
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

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }
    public void setDateOfBirth(LocalDate dateOfBirth) {
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

    public String getCountry() {
        return Country;
    }
    public void setCountry(String country) {
        Country = country;
    }

    public String getCity() {
        return City;
    }
    public void setCity(String city) {
        City = city;
    }

    public String getAddress() {
        return Address;
    }
    public void setAddress(String address) {
        Address = address;
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

    public LocalDateTime getAccountLockedUntil() {
        return accountLockedUntil;
    }
    public void setAccountLockedUntil(LocalDateTime accountLockedUntil) {
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

    public AccountProvider getAccountProvider() {
        return accountProvider;
    }
    public void setAccountProvider(AccountProvider accountProvider) {
        this.accountProvider = accountProvider;
    }

    public Account_User() {
        this.ID = Instant.now().toEpochMilli();
    }


    public Account_Role getRole() {
        return role == null ? role = retrieveEntityServiceFor(Account_Role.class).getById(getRoleId()).orElse(null) : null;
    }


    public static Account_User getByEmail(String email) {
        return retrieveServiceFor(Account_User.class).getWhere(Account_User.class, "Email = ?", email).orElse(null);
    }
    public static Account_User getByPhone(String phone) {
        return retrieveServiceFor(Account_User.class).getWhere(Account_User.class, "Phone = ?", phone).orElse(null);
    }
    public static Account_User getByAuthentication(Principal principal) {
        if (principal == null) return null;
        return retrieveServiceFor(Account_User.class).getWhere(Account_User.class, "Email = ?", principal.getName()).orElse(null);
    }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}