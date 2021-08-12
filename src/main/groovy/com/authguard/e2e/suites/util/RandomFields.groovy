package com.authguard.e2e.suites.util

import org.apache.commons.lang3.RandomStringUtils

class RandomFields {
    static String email() {
        return RandomStringUtils.randomAlphabetic(10) + "@test.org"
    }

    static String username() {
        return RandomStringUtils.randomAlphanumeric(10)
    }

    static String permissionGroup() {
        return RandomStringUtils.randomAlphabetic(5)
    }

    static String permissionName() {
        return RandomStringUtils.randomAlphabetic(5)
    }

    static String role() {
        return RandomStringUtils.randomAlphabetic(10)
    }

    static String password() {
        return RandomStringUtils.randomAlphanumeric(5).toLowerCase() +
                RandomStringUtils.randomAlphabetic(5).toUpperCase()
    }
}
