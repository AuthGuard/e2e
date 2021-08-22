package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.RandomFields
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow
import org.scenario.exceptions.TestFailuresExceptions

import static io.restassured.RestAssured.given

class CredentialsScenarios {
    @ScenarioDefinition
    Scenario password() {
        return new Scenario.Builder()
                .name("Credentials scenarios")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createAccount")
                        .step("createCredentials")
                        .step("updatePassword")
                        .step("addIdentifier")
                        .step("authenticateOldPassword")
                        .step("authenticateNewPassword")
                        .build())
                .build();
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createAccount(ScenarioContext context) {
        def idempotentKey = UUID.randomUUID().toString()

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        email     : [email: RandomFields.email(), verified: false]
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account successfully {}", parsed.id)

        context.put(ContextKeys.createdAccount, parsed)
    }

    @Step(description = "Create credentials")
    @CircuitBreaker
    void createCredentials(ScenarioContext context) {
        def createdAccount = context.get(ContextKeys.createdAccount)

        if (!createdAccount) {
            throw new TestFailuresExceptions("No account was found in the global context")
        }

        String idempotentKey = UUID.randomUUID().toString()
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

        context.put(ContextKeys.createdCredentials, parsed)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
        context.put(ContextKeys.accountPassword, password)
    }

    @Step(name = "Update password")
    void updatePassword(ScenarioContext context) {
        def credentials = context.get(ContextKeys.createdCredentials)
        def password = context.get(ContextKeys.accountPassword)

        def newPassword = RandomFields.password()

        given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        "plainPassword": newPassword
                ]))
                .when()
                .patch("/credentials/{credentialsId}/password")
                .then()
                .statusCode(200)
                .extract()

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }

    @Step(name = "Add identifier")
    void addIdentifier(ScenarioContext context) {
        def credentials = context.get(ContextKeys.createdCredentials)

        def newEmail = RandomFields.email()

        given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        "identifiers": [
                                [
                                        identifier: newEmail,
                                        type: "EMAIL"
                                ]
                        ]
                ]))
                .when()
                .patch("/credentials/{credentialsId}/identifiers")
                .then()
                .statusCode(200)
                .extract()
    }

    @Step(description = "Authenticate using the old password")
    void authenticateOldPassword(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.oldPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(400)
                .extract()
    }

    @Step(description = "Authenticate using the new password")
    void authenticateNewPassword(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
    }
}
