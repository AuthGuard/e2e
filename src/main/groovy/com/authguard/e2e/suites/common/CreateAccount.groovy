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

import static io.restassured.RestAssured.given

class CreateAccount {

    @ScenarioDefinition
    Scenario scenario() {
        return new Scenario.Builder()
                .name("Create account")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createAccount")
                        .build())
                .build()
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createAccount(ScenarioContext context,
                       @Name(ContextKeys.idempotentKey) idempotentKey,
                       @Name(ContextKeys.domain) domain) {
        def email = RandomFields.email()
        def phoneNumber = RandomFields.phoneNumber()
        def username = RandomFields.username()
        def password = RandomFields.password()

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        email     : [email: email, verified: false],
                        phoneNumber: [number: phoneNumber, verified: false],
                        metadata  : [
                                domain: "test-domain",
                                purpose: "E2E"
                        ],
                        identifiers   : [
                                [
                                        "identifier": username,
                                        "type"      : "USERNAME",
                                        active: true
                                ]
                        ],
                        "plainPassword": password,
                        domain: domain
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account successfully {}", parsed.id)

        def emailIdentifier = parsed.identifiers.find { it.type == "EMAIL" }
        def phoneNumberIdentifier = parsed.identifiers.find { it.type == "PHONE_NUMBER" }

        assert emailIdentifier != null : "Email identifier was not added"
        assert emailIdentifier.active : "Email identifier was created but is not active"
        assert emailIdentifier.domain == domain : "Email identifier was created but with the wrong domain"

        assert phoneNumberIdentifier != null : "Phone number identifier was not added"
        assert phoneNumberIdentifier.active : "Phone number identifier was created but is not active"
        assert phoneNumberIdentifier.domain == domain : "Phone number identifier was created but with the wrong domain"

        context.global().put(ContextKeys.createdAccount, parsed)
        context.global().put(ContextKeys.accountIdentifiers, parsed.identifiers)
        context.global().put(ContextKeys.accountPassword, password)
    }
}
