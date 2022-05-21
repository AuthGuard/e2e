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
                        .step("updatePassword")
                        .step("authenticateOldPassword")
                        .step("authenticateNewPassword")
                        .step("addIdentifier")
                        .step("authenticateNewIdentifier")
                        .step("removeIdentifier")
                        .step("authenticateOldIdentifier") // TODO https://github.com/AuthGuard/AuthGuard/issues/166
                        .step("replaceIdentifier")
                        .step("authenticateOldIdentifier")
                        .step("authenticateNewIdentifier")

                        .step("resetPasswordByToken")
                         // we can just repeat the same steps since it's the same logic
                        .step("authenticateOldPassword")
                        .step("authenticateNewPassword")

                        .step("resetPasswordByOldPassword")
                        // we can just repeat the same steps since it's the same logic
                        .step("authenticateOldPassword")
                        .step("authenticateNewPassword")
                        .build())
                .build();
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createAccount(ScenarioContext context) {
        def idempotentKey = UUID.randomUUID().toString()
        def username = RandomFields.username()
        def password = RandomFields.password()

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        email     : [email: RandomFields.email(), verified: false],
                        domain: "e2e",
                        identifiers   : [
                                [
                                        identifier: username,
                                        type: "USERNAME",
                                        active: true
                                ]
                        ],
                        "plainPassword": password,
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account successfully {}", parsed.id)

        context.put(ContextKeys.createdAccount, parsed)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
        context.put(ContextKeys.accountPassword, password)
    }

    @Step(name = "Update password")
    void updatePassword(ScenarioContext context) {
        def credentials = context.get(ContextKeys.createdAccount)
        def password = context.get(ContextKeys.accountPassword)

        def newPassword = RandomFields.password()

        def response = given()
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

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }

    @Step(name = "Add identifier")
    void addIdentifier(ScenarioContext context) {
        def credentials = context.get(ContextKeys.createdAccount)

        def newEmail = RandomFields.email()

        def response = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        "identifiers": [
                                [
                                        identifier: newEmail,
                                        type: "EMAIL",
                                        active: true
                                ]
                        ]
                ]))
                .when()
                .patch("/credentials/{credentialsId}/identifiers")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        def getCredentialsResponse = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .when()
                .get("/accounts/{credentialsId}")
                .then()
                .extract()

        def newParsed = Json.slurper.parseText(getCredentialsResponse.body().asString())

        context.put(ContextKeys.newIdentifier, newEmail)
        context.put(ContextKeys.accountIdentifiers, newParsed.identifiers)
    }

    @Step(name = "Remove identifier")
    void removeIdentifier(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def credentials = context.get(ContextKeys.createdAccount)

        def identifierToRemove = identifiers[0].identifier

        def response = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        "identifiers": [
                                [
                                        identifier: identifierToRemove,
                                        type: "USERNAME"
                                ]
                        ]
                ]))
                .when()
                .delete("/credentials/{credentialsId}/identifiers")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        def getCredentialsResponse = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .when()
                .get("/accounts/{credentialsId}")
                .then()
                .extract()

        def newParsed = Json.slurper.parseText(getCredentialsResponse.body().asString())

        context.put(ContextKeys.oldIdentifier, identifierToRemove)
        context.put(ContextKeys.accountIdentifiers, newParsed.identifiers)
    }

    @Step(name = "Replace identifier")
    void replaceIdentifier(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def credentials = context.get(ContextKeys.createdAccount)

        def identifierToReplace = identifiers[0].identifier
        def newUsername = RandomFields.username()

        def response = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        oldIdentifier: identifierToReplace,
                        identifiers: [
                                [
                                        identifier: newUsername,
                                        type: "USERNAME",
                                        active: true
                                ]
                        ]
                ]))
                .when()
                .patch("/credentials/{credentialsId}/identifiers")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.newIdentifier, newUsername)
        context.put(ContextKeys.oldIdentifier, identifierToReplace)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
    }

    @Step(description = "Authenticate using the old password")
    void authenticateOldPassword(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.oldPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
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

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .extract()

        if (response.statusCode() != 200) {
            throw new TestFailuresExceptions(String.format("Status %d, Body %s", response.statusCode(), response.body().asString()))
        }
    }

    @Step(description = "Authenticate using the old identifier")
    void authenticateOldIdentifier(ScenarioContext context) {
        def identifier = context.get(ContextKeys.oldIdentifier)
        def password = context.get(ContextKeys.accountPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(400)
                .extract()
    }

    @Step(description = "Authenticate using the new identifier")
    void authenticateNewIdentifier(ScenarioContext context) {
        def identifier = context.get(ContextKeys.newIdentifier)
        def password = context.get(ContextKeys.accountPassword)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .extract()

        if (response.statusCode() != 200) {
            throw new TestFailuresExceptions(String.format("Status %d, Body %s", response.statusCode(), response.body().asString()))
        }
    }

    @Step(name = "Reset password")
    void resetPasswordByToken(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        def newPassword = RandomFields.password()

        def tokenResponse = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        domain: "e2e"
                ]))
                .when()
                .post("/credentials/reset_token")
                .then()
                .statusCode(200)
                .extract()

        def parsedTokenResponse = Json.slurper.parseText(tokenResponse.body().asString())

        def response = given()
                .body(JsonOutput.toJson([
                        byToken: true,
                        resetToken: parsedTokenResponse.token,
                        newPassword: newPassword
                ]))
                .when()
                .post("/credentials/reset")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }

    @Step(name = "Reset password using old password")
    void resetPasswordByOldPassword(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        def newPassword = RandomFields.password()

        def response = given()
                .body(JsonOutput.toJson([
                        byToken: false,
                        identifier: identifiers[0].identifier,
                        oldPassword: password,
                        newPassword: newPassword,
                        domain: "e2e"
                ]))
                .when()
                .post("/credentials/reset")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }
}
