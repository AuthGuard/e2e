package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.Name
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class ApplicationScenarios {
    @ScenarioDefinition
    Scenario applicationsManagement() {
        return new Scenario.Builder()
                .name("General applications management")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createApp")
                        .step("deactivateApp")
                        .step("activateApp")
                        .step("generateApiKey")
                        .step("getApiKeyByAppId")
                        .step("verifyApiKey")
                        .step("deleteApiKey")
                        .step("deleteApp")
                        .build())
                .build()
    }

    @Step(description = "Create account")
    @CircuitBreaker
    void createApp(ScenarioContext context, @Name(ContextKeys.idempotentKey) idempotentKey) {
        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .body(JsonOutput.toJson([
                        name: "Test scenario app"
                ]))
                .when()
                .post("/apps")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created app successfully {}", parsed.id)

        context.put(ContextKeys.app, parsed)
    }

    @Step(name = "Deactivate application")
    void deactivateApp(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        def response = given()
                .pathParam("appId", app.id)
                .when()
                .patch("/apps/{appId}/deactivate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == false : "Account was not deactivated"
    }

    @Step(name = "Activate application")
    void activateApp(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        def response = given()
                .pathParam("appId", app.id)
                .when()
                .patch("/apps/{appId}/activate")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.active == true : "Account was not activated"
    }

    @Step(name = "Delete application")
    void deleteApp(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        given()
                .pathParam("appId", app.id)
                .when()
                .delete("/apps/{appId}")
                .then()
                .statusCode(200)
                .extract()

        given()
                .pathParam("appId", app.id)
                .when()
                .get("/apps/{appId}")
                .then()
                .statusCode(404)
                .extract()
    }

    @Step(name = "Generate API key for application")
    void generateApiKey(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        def response = given()
                .when()
                .body(JsonOutput.toJson([
                        appId: app.id
                ]))
                .post("/keys")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        context.put(ContextKeys.temporaryKey, parsed)
    }

    @Step(name = "Verify the API key")
    void verifyApiKey(ScenarioContext context) {
        def apiKey = context.get(ContextKeys.temporaryKey)
        def app = context.get(ContextKeys.app)

        def response = given()
                .when()
                .body(JsonOutput.toJson([
                        token: apiKey.key
                ]))
                .post("/keys/verify")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.id == app.id
    }

    @Step(name = "Get API keys for application")
    void getApiKeyByAppId(ScenarioContext context) {
        def app = context.get(ContextKeys.app)

        def response = given()
                .pathParam("appId", app.id)
                .when()
                .get("/apps/{appId}/keys")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.size() == 1 // the returned key is hashed so we can't compare
    }

    @Step(name = "Delete API key for application")
    void deleteApiKey(ScenarioContext context) {
        def apiKey = context.get(ContextKeys.temporaryKey)

        given()
                .pathParam("apiKeyId", apiKey.id)
                .when()
                .delete("/keys/{apiKeyId}")
                .then()
                .statusCode(200)
                .extract()

        // TODO uncomment, the bug was fixed in 0.9.0
//        given()
//                .pathParam("apiKeyId", apiKey.id)
//                .when()
//                .get("/keys/{apiKeyId}")
//                .then()
//                .statusCode(404)
//                .extract()
    }

}
