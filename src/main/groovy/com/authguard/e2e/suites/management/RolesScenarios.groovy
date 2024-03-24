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
                        .step("createAccountRole")
                        .step("createApplicationRole")
                        .step("grantAccountRole")
                        .step("revokeAccountRole")
                        .step("grantApplicationRole")
                        .step("revokeApplicationRole")
                        .step("createDuplicateRole")
                        .step("grantNonExistingRole")
                        .step("revokeNonGrantedRole")
                        .build())
                .build()
    }

    @Step
    void createAccountRole(ScenarioContext context) {
        def roleName = RandomFields.role()

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        "name": roleName,
                        domain: "e2e",
                        forAccounts: true,
                        forApplications: false
                ]))
                .when()
                .post("/domains/{domain}/roles")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created account role successfully {} {}", parsed.name, parsed.id)

        context.put(ContextKeys.accountRoleName, roleName)
    }

    @Step
    void createApplicationRole(ScenarioContext context) {
        def roleName = RandomFields.role()

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        "name": roleName,
                        domain: "e2e",
                        forAccounts: false,
                        forApplications: true
                ]))
                .when()
                .post("/domains/{domain}/roles")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created applications role successfully {} {}", parsed.name, parsed.id)

        context.put(ContextKeys.appRoleName, roleName)
    }

    @Step
    void grantAccountRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.accountRoleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        def r = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        roles: [ roleName ],
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/roles")
                .then()
//                .statusCode(200)
                .extract()

        def accountResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert parsed.roles.contains(roleName)
    }

    @Step
    void revokeAccountRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.accountRoleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(200)
                .extract()

        def accountResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert !parsed.roles.contains(roleName)
    }

    @Step
    void grantApplicationRole(ScenarioContext context) {
        def app = context.global().get(ContextKeys.createdApplication)
        def roleName = (String) context.get(ContextKeys.appRoleName)

        if (!app) {
            throw new TestFailuresExceptions("No application was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        roles: [ roleName ],
                ]))
                .when()
                .patch("/domains/{domain}/apps/" + app.id + "/roles")
                .then()
                .statusCode(200)
                .extract()

        def appResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/apps/" + app.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(appResponse.body().asString())

        assert parsed.roles.contains(roleName)
    }

    @Step
    void revokeApplicationRole(ScenarioContext context) {
        def app = context.global().get(ContextKeys.createdApplication)
        def roleName = (String) context.get(ContextKeys.appRoleName)

        if (!app) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/domains/{domain}/apps/" + app.id + "/roles")
                .then()
                .statusCode(200)
                .extract()

        def appResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/apps/" + app.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(appResponse.body().asString())

        assert !parsed.roles.contains(roleName)
    }

    @Step
    void createDuplicateRole(ScenarioContext context) {
        def roleName = (String) context.get(ContextKeys.accountRoleName)

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        "name": roleName,
                        domain: "e2e",
                        forAccounts: true,
                        forApplications: false
                ]))
                .when()
                .post("/domains/{domain}/roles")
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
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        roles: [ "made up" ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(400)
                .extract()

        def accountResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        assert !parsed.roles.contains("made-up")
    }

    @Step
    void revokeNonGrantedRole(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def roleName = (String) context.get(ContextKeys.accountRoleName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!roleName) {
            throw new TestFailuresExceptions("No role was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        roles: [ roleName ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/roles")
                .then()
                .statusCode(200)
                .extract()
    }
}
