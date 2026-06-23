package org.solarframework.auth.obj;

import jakarta.persistence.*;
import org.solarframework.auth.obj.enums.AccountProvider;
import org.solarframework.auth.obj.enums.Gender;
import org.solarframework.auth.obj.enums.UserStatus;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.lang.Nationalities;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Entity
@Table(name = "account_user")
public class Account_AuthorizedPath extends DatabaseObject.ID_OBJ_RECORD<Long, Account_AuthorizedPath> {

    @Column(name = "Path", nullable = false, unique = true, length = 128)
    private String path;
    @Column(name = "Level", length = 128, nullable = false)
    private String email;
    @Column(name = "PasswordHash", length = 512, nullable = false)
    private String passwordHash;


}