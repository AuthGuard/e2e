package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.RandomFields
import groovy.json.JsonOutput
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow
import org.scenario.exceptions.TestFailuresExceptions

import static io.restassured.RestAssured.given

class RolesScenarios {

    @ScenarioDefinition
    Scenario rolesScenario() {
        return new Scenario.Builder()
                .name("Roles scenario")
                .description("Roles management scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createRole")
                        .step("grantRole")
                        .step("revokeRole")
                        .step("createDuplicateRole")
                        .step("grantNonExistingRole")
                        .step("revokeNonGrantedRole")
                        .build())
                .build()
    }

    @Step
    void createRole(ScenarioContext context) {
        def roleName = RandomFields.role()

        def response = given()
                .body(JsonOutput.toJson([
                        "name": roleName
                ]))
                .when()
                .post("/roles")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created role successfully {} {}", parsed.name, parsed.id)

        context.put(ContextKeys.roleName, roleName)
    }

    @Step
    void grantRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.roleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(200)
                .extract()

        def accountResponse = given()
                .when()
                .get("/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert parsed.roles.contains(roleName)
    }

    @Step
    void revokeRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.roleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(200)
                .extract()

        def accountResponse = given()
                .when()
                .get("/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert !parsed.roles.contains(roleName)
    }

    @Step
    void createDuplicateRole(ScenarioContext context) {
        def roleName = (String) context.get(ContextKeys.roleName)

        given()
                .body(JsonOutput.toJson([
                        "name": roleName
                ]))
                .when()
                .post("/roles")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step
    void grantNonExistingRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        given()
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        roles: [ "made up" ]
                ]))
                .when()
                .patch("/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(400)
                .extract()

        def accountResponse = given()
                .when()
                .get("/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert !parsed.roles.contains("made-up")
    }

    @Step
    void revokeNonGrantedRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.roleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(200)
                .extract()
    }
}
