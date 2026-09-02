package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveColumnsTest {

    @Test
    void refusesTheColumnsThatHoldSecrets() {
        assertTrue(SensitiveColumns.isSensitive("PasswordHash"));
        assertTrue(SensitiveColumns.isSensitive("password"));
        assertTrue(SensitiveColumns.isSensitive("ClientSecret"));
        assertTrue(SensitiveColumns.isSensitive("access_token"));
        assertTrue(SensitiveColumns.isSensitive("API-KEY"));
        assertTrue(SensitiveColumns.isSensitive("PwdSalt"));
    }

    @Test
    void allowsOrdinaryColumns() {
        assertFalse(SensitiveColumns.isSensitive("ID"));
        assertFalse(SensitiveColumns.isSensitive("FirstName"));
        assertFalse(SensitiveColumns.isSensitive("CreatedAt"));
        assertFalse(SensitiveColumns.isSensitive("Email"));
    }

    @Test
    void treatsAMissingNameAsHarmless() {
        assertFalse(SensitiveColumns.isSensitive(null));
        assertFalse(SensitiveColumns.isSensitive(""));
    }

    @Test
    void keepsTheOrderOfWhatSurvives() {
        assertEquals(List.of("ID", "Email"), SensitiveColumns.servable(List.of("ID", "PasswordHash", "Email", "MfaSecret")));
        assertEquals(List.of(), SensitiveColumns.servable(null));
    }

    @Test
    void dropsStoredFilesAsWellAsSecrets() {
        List<String> asked = List.of("ID", "Name", "Avatar", "PasswordHash");
        assertEquals(List.of("ID", "Name"), SensitiveColumns.servable(asked, Set.of("Avatar")));
        assertEquals(List.of("ID", "Name", "Avatar"), SensitiveColumns.servable(asked, Set.of()), "a table with no stored file loses only the secret");
        assertEquals(List.of("ID", "Name", "Avatar"), SensitiveColumns.servable(asked, null), "nothing known about the files is not a reason to serve the secret");
    }
}
