package com.authguard.e2e.suites.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class TestsConfig {
    private static Config config

    static {
        ConfigFactory.invalidateCaches()

        config = ConfigFactory.load()
    }

    static Config get() {
        return config
    }

    static String baseUrl() {
        return config.getConfig("authguard").getString("baseUrl")
    }

    static String key() {
        return config.getConfig("authguard").getString("key")
    }

    static String otaUsername() {
        return config.getConfig("authguard").getString("ota_username")
    }

    static String otaPassword() {
        return config.getConfig("authguard").getString("ota_password")
    }
}
