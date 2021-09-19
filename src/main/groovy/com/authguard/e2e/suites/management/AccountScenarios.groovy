package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.RandomFields
import groovy.json.JsonOutput
import org.scenario.annotations.Name
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class AccountScenarios {

    @ScenarioDefinition
    Scenario duplicateCreationScenarios() {
        return new Scenario.Builder()
                .name("Create account and credentials with duplicate values")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        // account
                        .step("createAccountDuplicateIdempotentKey")
                        // credentials
                        .step("createCredentialsDuplicateIdempotentKey")
                        .step("createCredentialsDuplicateUsername")
                        .step("createCredentialsDuplicateEmail")
                        .build())
                .build()
    }

    @ScenarioDefinition
    Scenario accountManagement() {
        return new Scenario.Builder()
                .name("General account management")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("deactivateAccount")
                        .step("activateAccount")
                        .step("updateEmail")
                        .step("updateBackupEmail")
                        .step("accountExists")
                        .step("accountDoesNotExist")
                        .build())
                .build()
    }

    @Step(name = "Create account with the same idempotent key")
    void createAccountDuplicateIdempotentKey(@Name(ContextKeys.idempotentKey) String idempotentKey) {
        given()
                .header(Headers.idempotentKey, idempotentKey)
                .body("{}")
                .when()
                .post("/accounts")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Create account with the same email")
    void createAccountDuplicateEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)

        given()
                .header(Headers.idempotentKey, UUID.randomUUID())
                .body(JsonOutput.toJson([
                        email     : [email: account.email.email, verified: false]
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Create credentials with the same idempotent key")
    void createCredentialsDuplicateIdempotentKey(@Name(ContextKeys.idempotentKey) String idempotentKey) {
        given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        accountId     : "some account",
                        identifiers   : [
                                [
                                        "identifier": "some username",
                                        "type"      : "USERNAME"
                                ]
                        ],
                        "plainPassword": "some password"
                ]))
                .when()
                .post("/credentials")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Create credentials with the same username identifier")
    void createCredentialsDuplicateUsername(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def identifiers = context.global().get(ContextKeys.accountIdentifiers)

        def username = identifiers.find { it.type == "USERNAME" }.identifier

        given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        accountId     : account.id,
                        identifiers   : [
                                [
                                        "identifier": username,
                                        "type"      : "USERNAME"
                                ]
                        ],
                        "plainPassword": RandomFields.password()
                ]))
                .when()
                .post("/credentials")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Create credentials with the same email identifier")
    void createCredentialsDuplicateEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def identifiers = context.global().get(ContextKeys.accountIdentifiers)

        def email = identifiers.find { it.type == "EMAIL" }.identifier

        given()
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        accountId     : account.id,
                        identifiers   : [
                                [
                                        "identifier": email,
                                        "type"      : "EMAIL"
                                ]
                        ],
                        "plainPassword": RandomFields.password()
                ]))
                .when()
                .post("/credentials")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Deactivate account")
    void deactivateAccount(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)

        def response = given()
                .pathParam("accountId", account.id)
                .when()
                .patch("/accounts/{accountId}/deactivate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == false : "Account was not deactivated"
    }

    @Step(name = "Activate account")
    void activateAccount(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)

        def response = given()
                .pathParam("accountId", account.id)
                .when()
                .patch("/accounts/{accountId}/activate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == true : "Account was not activated"
    }

    @Step(name = "Update email")
    void updateEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def newEmail = RandomFields.email()

        def response = given()
                .pathParam("accountId", account.id)
                .body(JsonOutput.toJson([
                        "email": [
                                "email": newEmail
                        ]
                ]))
                .when()
                .patch("/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.email.email == newEmail : "Email was not updated"
        assert parsed.email.verified == false : "Email was verified even though it is not supposed to be"

        if (parsed.backupEmail) {
            assert parsed.backupEmail.email != newEmail : "Backup email was updated when only the primary should have been updated"
        }

        context.global().put(ContextKeys.createdAccount, parsed)
    }

    @Step(name = "Update email")
    void updateBackupEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def newEmail = RandomFields.email()

        def response = given()
                .pathParam("accountId", account.id)
                .body(JsonOutput.toJson([
                        "backupEmail": [
                                "email": newEmail
                        ]
                ]))
                .when()
                .patch("/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.backupEmail != null : "Backup email was null"
        assert parsed.backupEmail.email == newEmail : "Backup email was not updated"
        assert parsed.backupEmail.verified == false : "Backup email was verified even though it is not supposed to be"
        assert parsed.email.email != newEmail : "Email was updated when only the backup should have been updated"

        context.global().put(ContextKeys.createdAccount, parsed)
    }

    @Step(name = "Check if account exists")
    void accountExists(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def email = account.email.email

        given()
                .pathParam("email", email)
                .when()
                .get("/accounts/email/{email}/exists")
                .then()
                .statusCode(200)
                .extract()
    }

    @Step(name = "Check if account exists")
    void accountDoesNotExist(ScenarioContext context) {
        def email = "nonexistent"

        given()
                .pathParam("email", email)
                .when()
                .get("/accounts/email/{email}/exists")
                .then()
                .statusCode(404)
                .extract()
    }
}
