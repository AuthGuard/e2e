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

class CreateApplication {

    @ScenarioDefinition
    Scenario scenario() {
        return new Scenario.Builder()
                .name("Create application")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createApplication")
                        .build())
                .build()
    }

    @Step(description = "Create application")
    @CircuitBreaker
    void createApplication(ScenarioContext context,
                           @Name(ContextKeys.domain) domain) {
        def response = given()
                .pathParam("domain", domain)
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        externalId: "external",
                        domain: domain,
                        name: "Global test account"
                ]))
                .when()
                .post("/domains/{domain}/apps")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created app successfully {}", parsed.id)

        context.global().put(ContextKeys.createdApplication, parsed)
    }
}
