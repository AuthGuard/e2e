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

class ActionTokenScenarios {
    @ScenarioDefinition
    Scenario accessToken() {
        return new Scenario.Builder()
                .name("Action token scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createActionToken")
                        .step("verifyActionToken")
                        .step("verifyActionTokenWrongAction")
                        .build())
                .build();
    }

    @Step(description = "Generate action token")
    @CircuitBreaker
    void createActionToken(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        def response = given()
                .body(JsonOutput.toJson([
                        type: "BASIC",
                        action: "reset",
                        basic: [
                                identifier: identifiers[0].identifier,
                                password: password
                        ]
                ]))
                .when()
                .post("/actions/token")
                .then()
                //.statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated action token successfully {}", parsed)

        assert parsed.action == null

        context.put(ContextKeys.token, parsed.token)
    }

    @Step(description = "Verify action token")
    void verifyActionToken(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def token = context.get(ContextKeys.token)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: token
                ]))
                .when()
                .post("/actions/verify?token=" + token + "&action=reset")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())
    }

    @Step(description = "Verify action token with wrong action")
    void verifyActionTokenWrongAction(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def token = context.get(ContextKeys.token)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: token
                ]))
                .when()
                .post("/actions/verify?token=" + token + "&action=wrong")
                .then()
                .statusCode(400)
                .extract()
    }
}
