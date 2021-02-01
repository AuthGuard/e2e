package com.authguard.e2e.suites.util

import org.slf4j.LoggerFactory

class Logger {
    private final static org.slf4j.Logger log = LoggerFactory.getLogger("tests")

    static org.slf4j.Logger get() {
        return log
    }
}
