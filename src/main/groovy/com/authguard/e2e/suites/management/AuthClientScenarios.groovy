package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.RandomFields
import com.authguard.e2e.suites.util.ResponseAssertions
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class AuthClientScenarios {
    @ScenarioDefinition
    Scenario authEndpointsScenario() {
        return new Scenario.Builder()
                .name("Auth client endpoints")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createAuthClient")
                        .step("createApiKey")
                        .step("createAccountWithRoles")
                        .step("createAccountWithPermissions")
                        .step("createResetToken")
                        .step("createAccountWithDifferentDomain")
                        .step("createResetTokenDifferentDomain")
                        .step("authenticateWithDifferentDomain")
                        .step("authenticateWithSourceIp")
                        .step("authenticateWithUserAgent")
                        .build())
                .build()
    }

    @Step(description = "Create auth client app")
    @CircuitBreaker
    void createAuthClient(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        name: "Test auth app",
                        clientType: "AUTH",
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/clients")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created app successfully {}", parsed.id)

        context.put(ContextKeys.app, parsed)
    }

    @Step
    void createApiKey(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        forClient: true,
                        appId: app.id,
                        keyType: "default"
                ]))
                .when()
                .post("/domains/{domain}/keys")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated API key successfully {}", parsed.key)

        context.put(ContextKeys.key, parsed.key)
    }

    @Step(description = "Create account with roles (should be 403)")
    void createAccountWithRoles(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        roles: [ "role-1" ],
                        identifiers: [
                                [
                                        "identifier": RandomFields.username(),
                                        "type"      : "USERNAME",
                                        active: true
                                ],
                        ],
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(description = "Create account with permissions (should be 403)")
    void createAccountWithPermissions(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        permissions: [
                                [
                                        group: "test",
                                        name: "read"
                                ]
                        ],
                        identifiers: [
                                [
                                        "identifier": RandomFields.username(),
                                        "type"      : "USERNAME",
                                        active: true
                                ],
                        ],
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(name = "Reset password")
    void createResetToken(ScenarioContext context) {
        def key = context.get(ContextKeys.key)
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        "identifier": identifiers[0].identifier,
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/credentials/reset_token")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 200)

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.token == null
    }

    @Step(description = "Create account with another domain (should be 403)")
    void createAccountWithDifferentDomain(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "other")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        domain: "other",
                        identifiers: [
                                [
                                        "identifier": RandomFields.username(),
                                        "type"      : "USERNAME",
                                        active: true
                                ],
                        ],
                ]))
                .when()
                .post("/domains/{domain}/accounts")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(name = "Reset password with different domain (should be 403)")
    void createResetTokenDifferentDomain(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "other")
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        "identifier": "identifier",
                        domain: "other"
                ]))
                .when()
                .post("/domains/{domain}/credentials/reset_token")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(description = "Authenticate with different domain (should be 403)")
    void authenticateWithDifferentDomain(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "e2e")
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        identifier: "identifier",
                        password: "password",
                        domain: "other"
                ]))
                .when()
                .post("/domains/{domain}/auth/authenticate")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(description = "Authenticate with source IP (should be 403)")
    void authenticateWithSourceIp(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "e2e")
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        identifier: "identifier",
                        password: "password",
                        domain: "e2e",
                        sourceIp: "127.0.0.1"
                ]))
                .when()
                .post("/domains/{domain}/auth/authenticate")
                .then()
                .statusCode(403)
                .extract()
    }

    @Step(description = "Authenticate with user agent (should be 403)")
    void authenticateWithUserAgent(ScenarioContext context) {
        def key = context.get(ContextKeys.key)

        given()
                .pathParam("domain", "e2e")
                .header(Headers.authorization, "Bearer " + key)
                .body(JsonOutput.toJson([
                        identifier: "identifier",
                        password: "password",
                        domain: "e2e",
                        userAgent: "client"
                ]))
                .when()
                .post("/domains/{domain}/auth/authenticate")
                .then()
                .statusCode(403)
                .extract()
    }
}
