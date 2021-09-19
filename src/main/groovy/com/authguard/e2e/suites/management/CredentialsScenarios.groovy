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
                        .step("authenticateOldPassword")
                        .step("authenticateNewPassword")
                        .step("addIdentifier")
                        .step("removeIdentifier")
                        //.step("authenticateOldIdentifier") // TODO https://github.com/AuthGuard/AuthGuard/issues/166
                        .step("authenticateNewIdentifier")
                        .step("replaceIdentifier")
                        .step("authenticateOldIdentifier")
                        .step("authenticateNewIdentifier")
                        .step("resetPassword")
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
                                        identifier: username,
                                        type: "USERNAME"
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

        def response = given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .pathParam("credentialsId", credentials.id)
                .body(JsonOutput.toJson([
                        "plainPassword": newPassword
                ]))
                .when()
                .patch("/credentials/{credentialsId}/password")
                .then()
                //.statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }

    @Step(name = "Add identifier")
    void addIdentifier(ScenarioContext context) {
        def credentials = context.get(ContextKeys.createdCredentials)

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
                .get("/credentials/{credentialsId}")
                .then()
                .extract()

        def newParsed = Json.slurper.parseText(getCredentialsResponse.body().asString())

        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
    }

    @Step(name = "Remove identifier")
    void removeIdentifier(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def credentials = context.get(ContextKeys.createdCredentials)

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
                .get("/credentials/{credentialsId}")
                .then()
                .extract()

        def newParsed = Json.slurper.parseText(getCredentialsResponse.body().asString())

        context.put(ContextKeys.oldIdentifier, identifierToRemove)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
    }

    @Step(name = "Replace identifier")
    void replaceIdentifier(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def credentials = context.get(ContextKeys.createdCredentials)

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
                                        type: "USERNAME"
                                ]
                        ]
                ]))
                .when()
                .patch("/credentials/{credentialsId}/identifiers")
                .then()
                //.statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

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

    @Step(description = "Authenticate using the old identifier")
    void authenticateOldIdentifier(ScenarioContext context) {
        def identifier = context.get(ContextKeys.oldIdentifier)
        def password = context.get(ContextKeys.accountPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifier,
                        password: password
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(400)
                .extract()
    }

    @Step(description = "Authenticate using the new identifier")
    void authenticateNewIdentifier(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[identifiers.size() - 1].identifier,
                        password: password
                ]))
                .when()
                .post("/auth/authenticate")
                .then()
                .statusCode(200)
                .extract()
    }

    @Step(name = "Reset password")
    void resetPassword(ScenarioContext context) {
        def identifiers = (List) context.get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        def newPassword = RandomFields.password()

        def tokenResponse = given()
                .body(JsonOutput.toJson([
                        "identifier": identifiers[0].identifier
                ]))
                .when()
                .post("/credentials/reset_token")
                .then()
                .statusCode(200)
                .extract()

        def parsedTokenResponse = Json.slurper.parseText(tokenResponse.body().asString())

        def response = given()
                .body(JsonOutput.toJson([
                        resetToken: parsedTokenResponse.token,
                        plainPassword: newPassword
                ]))
                .when()
                .post("/credentials/reset")
                .then()
                //.statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.oldPassword, password)
        context.put(ContextKeys.accountPassword, newPassword)
    }
}
