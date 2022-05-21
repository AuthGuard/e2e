package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.ResponseAssertions
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class SessionScenarios {
    @ScenarioDefinition
    Scenario session() {
        return new Scenario.Builder()
                .name("Session scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("authenticate")
                        .step("verifySession")
                        .step("logout")
                        .step("verifyAgain")
                        .build())
                .build();
    }

    @Step(description = "Generate session")
    @CircuitBreaker
    void authenticate(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/auth/exchange?from=basic&to=sessionToken")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 200)

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated session token successfully {}", parsed)

        context.put(ContextKeys.token, parsed.token)
    }

    @Step(name = "Verify session")
    void verifySession(ScenarioContext context) {
        def sessionToken = context.get(ContextKeys.token)

        def response = given()
                .body(JsonOutput.toJson([
                        token: sessionToken
                ]))
                .when()
                .post("/auth/exchange?from=sessionToken&to=session")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())
    }

    @Step(name = "Logout")
    void logout(ScenarioContext context) {
        def sessionToken = context.get(ContextKeys.token)

        def response = given()
                .body(JsonOutput.toJson([
                        token: sessionToken
                ]))
                .when()
                .post("/auth/exchange/clear?tokenType=sessionToken")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())
    }

    @Step(name = "Verify session")
    void verifyAgain(ScenarioContext context) {
        def sessionToken = context.get(ContextKeys.token)

        def response = given()
                .body(JsonOutput.toJson([
                        token: sessionToken
                ]))
                .when()
                .post("/auth/exchange?from=sessionToken&to=session")
                .then()
                //.statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "TK.022"
    }
}
