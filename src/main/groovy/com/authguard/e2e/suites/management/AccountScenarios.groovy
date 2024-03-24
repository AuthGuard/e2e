package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
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
                        .step("createCredentialsDuplicateUsername")
                        .build())
                .build()
    }

    @ScenarioDefinition
    Scenario accountManagement() {
        return new Scenario.Builder()
                .name("General account management")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createAccount")
                        .step("deactivateAccount")
                        .step("authenticateDeactivatedAccount")
                        .step("activateAccount")
                        .step("updateEmail")
                        .step("updateBackupEmail")
                        .step("updatePhoneNumber")
                        .step("accountExists")
                        .step("accountDoesNotExist")
                        .build())
                .build()
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createAccount(ScenarioContext context) {
        def idempotentKey = UUID.randomUUID().toString()
        def username = RandomFields.username()
        def password = RandomFields.password()

        def response = given()
                .pathParam("domain", "e2e")
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
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account successfully {}", parsed.id)

        context.put(ContextKeys.createdAccount, parsed)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
        context.put(ContextKeys.accountPassword, password)
    }

    @Step(name = "Create account with the same idempotent key")
    void createAccountDuplicateIdempotentKey(@Name(ContextKeys.idempotentKey) String idempotentKey) {
        given()
                .header(Headers.idempotentKey, idempotentKey)
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        identifiers   : [
                                [
                                        "identifier": RandomFields.username(),
                                        "type"      : "USERNAME",
                                        active: true
                                ]
                        ],
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Create account with the same email")
    void createAccountDuplicateEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)

        given()
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID())
                .body(JsonOutput.toJson([
                        email     : [
                                email: account.email.email,
                                verified: false
                        ],
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/accounts")
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
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        identifiers   : [
                                [
                                        "identifier": username,
                                        "type"      : "USERNAME"
                                ]
                        ],
                        "plainPassword": RandomFields.password(),
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step(name = "Deactivate account")
    void deactivateAccount(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .when()
                .patch("/domains/{domain}/accounts/{accountId}/deactivate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == false : "Account was not deactivated"
    }

    @Step(description = "Authenticate with a deactivated account")
    @CircuitBreaker
    void authenticateDeactivatedAccount(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.get(ContextKeys.accountPassword)

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/auth/authenticate")
                .then()
                .statusCode(400)
                .extract()
    }

    @Step(name = "Activate account")
    void activateAccount(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .when()
                .patch("/domains/{domain}/accounts/{accountId}/activate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == true : "Account was not activated"
    }

    @Step(name = "Update email")
    void updateEmail(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)
        def newEmail = RandomFields.email()

        def updateRequest = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .body(JsonOutput.toJson([
                        "email": [
                                "email": newEmail
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsedUpdateResponse = Json.slurper.parseText(updateRequest.body().asString())

        def getRequest = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .when()
                .get("/domains/{domain}/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsedGetResponse = Json.slurper.parseText(getRequest.body().asString())

        assert parsedGetResponse.email.email == newEmail : "Email was not updated"
        assert parsedGetResponse.email.verified == false : "Email was verified even though it is not supposed to be"

        def emailIdentifier = parsedGetResponse.identifiers.find { it.type == "EMAIL" }

        assert emailIdentifier != null : "Email identifier was not added"
        assert emailIdentifier.identifier == newEmail : "Email was updated but the identifier was not"
        assert parsedGetResponse.active : "Email identifier was created but is not active"
        assert parsedGetResponse.domain == "e2e" : "Email identifier was created but with the wrong domain"

        if (parsedUpdateResponse.backupEmail) {
            assert parsedGetResponse.backupEmail.email != newEmail : "Backup email was updated when only the primary should have been updated"
        }

        context.put(ContextKeys.createdAccount, parsedUpdateResponse)
        context.put(ContextKeys.accountIdentifiers, parsedUpdateResponse.identifiers)
    }

    @Step(name = "Update phone number")
    void updatePhoneNumber(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)
        def newNumber = RandomFields.phoneNumber()

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .body(JsonOutput.toJson([
                        "phoneNumber": [
                                "number": newNumber
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.phoneNumber.number == newNumber : "Phone number was not updated"
        assert parsed.phoneNumber.verified == false : "Phone number was verified even though it is not supposed to be"

        def phoneNumberIdentifier = parsed.identifiers.find { it.type == "PHONE_NUMBER" }

        assert phoneNumberIdentifier.identifier == newNumber : "Phone number was updated but the identifier was not"
        assert phoneNumberIdentifier != null : "Phone number identifier was not added"
        assert phoneNumberIdentifier.active : "Phone number identifier was created but is not active"
        assert phoneNumberIdentifier.domain == "e2e" : "Phone number identifier was created but with the wrong domain"

        context.put(ContextKeys.createdAccount, parsed)
        context.put(ContextKeys.accountIdentifiers, parsed.identifiers)
    }

    @Step(name = "Update email")
    void updateBackupEmail(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)
        def newEmail = RandomFields.email()

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("accountId", account.id)
                .body(JsonOutput.toJson([
                        "backupEmail": [
                                "email": newEmail
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/{accountId}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.backupEmail != null : "Backup email was null"
        assert parsed.backupEmail.email == newEmail : "Backup email was not updated"
        assert parsed.backupEmail.verified == false : "Backup email was verified even though it is not supposed to be"
        assert parsed.email.email != newEmail : "Email was updated when only the backup should have been updated"

        context.put(ContextKeys.createdAccount, parsed)
    }

    @Step(name = "Check if account exists")
    void accountExists(ScenarioContext context) {
        def account = context.get(ContextKeys.createdAccount)
        def email = account.email.email

        given()
                .pathParam("domain", "e2e")
                .pathParam("email", email)
                .when()
                .get("/domains/{domain}/accounts/email/{email}/exists")
                .then()
                .statusCode(200)
                .extract()
    }

    @Step(name = "Check if account exists")
    void accountDoesNotExist(ScenarioContext context) {
        def email = "nonexistent"

        given()
                .pathParam("domain", "e2e")
                .pathParam("email", email)
                .when()
                .get("/domains/{domain}/accounts/email/{email}/exists")
                .then()
                .statusCode(404)
                .extract()
    }
}
