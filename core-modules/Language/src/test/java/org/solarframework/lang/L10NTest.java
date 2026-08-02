package org.solarframework.lang;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class L10NTest {

    @AfterEach
    void resetBundle() {
        L10N.RB = null;
    }

}
