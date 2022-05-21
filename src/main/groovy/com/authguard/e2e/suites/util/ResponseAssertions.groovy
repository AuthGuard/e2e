package com.authguard.e2e.suites.util

import io.restassured.response.ExtractableResponse
import org.scenario.exceptions.TestFailuresExceptions

class ResponseAssertions {
    static def assertStatusCode(ExtractableResponse response, int code) {
        if (response.statusCode() != code) {
            throw new TestFailuresExceptions(String.format("Status %d, Body %s",
                    response.statusCode(), response.body().asString()))
        }
    }
}
