package com.authguard.e2e.suites.common

import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.RandomFields
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.Name
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow
import org.scenario.exceptions.TestFailuresExceptions

import static io.restassured.RestAssured.given

class CreateAccount {

    @ScenarioDefinition
    Scenario scenario() {
        return new Scenario.Builder()
                .name("Create account")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createAccount")
                        .step("createCredentials")
                        .build())
                .build()
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createAccount(ScenarioContext context, @Name(ContextKeys.idempotentKey) idempotentKey) {
        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        email     : [email: RandomFields.email(), verified: false]
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account successfully {}", parsed.id)

        context.global().put(ContextKeys.createdAccount, parsed)
    }

    @Step(description = "Create credentials")
    @CircuitBreaker
    void createCredentials(ScenarioContext context, @Name(ContextKeys.idempotentKey) idempotentKey) {
        def createdAccount = context.global().get(ContextKeys.createdAccount)

        if (!createdAccount) {
            throw new TestFailuresExceptions("No account was found in the global context")
        }

        String email = createdAccount.email
        String username = RandomFields.username()
        String password = RandomFields.password()

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        accountId     : createdAccount.id,
                        identifiers   : [
                            [
                                    "identifier": username,
                                    "type"      : "USERNAME"
                            ],
                            [
                                    "identifier": email,
                                    "type"      : "EMAIL"
                            ]
                        ],
                        "plainPassword": password
                ]))
                .when()
                .post("/credentials")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created credentials successfully {}", parsed.id)

        context.global().put(ContextKeys.accountIdentifiers, parsed.identifiers)
    }
}
