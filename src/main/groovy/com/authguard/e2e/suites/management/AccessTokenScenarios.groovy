package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class AccessTokenScenarios {
    @ScenarioDefinition
    Scenario accessToken() {
        return new Scenario.Builder()
                .name("Access token scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("authenticate")
                        .step("refresh")
                        .step("refreshAgain")
                        .step("logout")
                        .step("refreshAfterLogout")
                        .build())
                .build();
    }

    @Step(description = "Generate access token")
    @CircuitBreaker
    void authenticate(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/auth/exchange?from=basic&to=accessToken")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated access token successfully {}", parsed)

        context.put(ContextKeys.token, parsed.token)
        context.put(ContextKeys.refreshToken, parsed.refreshToken)
    }

    @Step(name = "Refresh token")
    void refresh(ScenarioContext context) {
        def refreshToken = context.get(ContextKeys.refreshToken)

        def response = given()
                .body(JsonOutput.toJson([
                        token: refreshToken
                ]))
                .when()
                .post("/auth/exchange?from=refresh&to=accessToken")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Refreshed access token successfully {}", parsed.token)

        context.put(ContextKeys.token, parsed.token)
        context.put(ContextKeys.oldRefreshToken, refreshToken)
        context.put(ContextKeys.refreshToken, parsed.refreshToken)
    }

    @Step(name = "Refresh token with a used refresh token")
    void refreshAgain(ScenarioContext context) {
        def refreshToken = context.get(ContextKeys.oldRefreshToken)

        def response = given()
                .body(JsonOutput.toJson([
                        token: refreshToken
                ]))
                .when()
                .post("/auth/exchange?from=refresh&to=accessToken")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "TK.022"
    }

    @Step(name = "Logout")
    void logout(ScenarioContext context) {
        def refreshToken = context.get(ContextKeys.refreshToken)

        def response = given()
                .body(JsonOutput.toJson([
                        token: refreshToken
                ]))
                .when()
                .post("/auth/exchange/clear?tokenType=accessToken")
                .then()
                .statusCode(200)
                .extract()
    }

    @Step(name = "Refresh token after logging out")
    void refreshAfterLogout(ScenarioContext context) {
        def refreshToken = context.get(ContextKeys.refreshToken)

        def response = given()
                .body(JsonOutput.toJson([
                        token: refreshToken
                ]))
                .when()
                .post("/auth/exchange?from=refresh&to=accessToken")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "TK.022"
    }
}
