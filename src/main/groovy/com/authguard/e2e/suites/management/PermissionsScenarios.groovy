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

class PermissionsScenarios {
    @ScenarioDefinition
    Scenario permissionsScenario() {
        return new Scenario.Builder()
                .name("Permissions scenario")
                .description("Permissions management scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createPermission")
                        .step("grantPermission")
                        .step("revokePermission")
                        .step("createDuplicatePermission")
                        .step("grantNonExistingPermission")
                        .step("revokeNonGrantedPermission")
                        .build())
                .build()
    }

    @Step
    void createPermission(ScenarioContext context) {
        def permissionGroup = RandomFields.permissionGroup()
        def permissionName = RandomFields.permissionName()

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        group: permissionGroup,
                        name: permissionName,
                        domain: "e2e",
                        forAccounts: true,
                        forApplications: false
                ]))
                .when()
                .post("/domains/{domain}/permissions")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created permission successfully {} {}", parsed.name, parsed.id)

        context.put(ContextKeys.permissionGroup, permissionGroup)
        context.put(ContextKeys.permissionName, permissionName)
    }

    @Step
    void grantPermission(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def permissionGroup = (String) context.get(ContextKeys.permissionGroup)
        def permissionName = (String) context.get(ContextKeys.permissionName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!permissionName) {
            throw new TestFailuresExceptions("No permission was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        permissions: [
                                [
                                        group: permissionGroup,
                                        name: permissionName
                                ]
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/permissions")
                .then()
                .extract()

        def accountResponse = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/accounts/" + userAccount.id)
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(accountResponse.body().asString())

        // TODO remove ID and other fields from permissions in an account or an app
        assert parsed.permissions[0].group == permissionGroup
        assert parsed.permissions[0].name == permissionName
    }

    @Step
    void revokePermission(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def permissionGroup = (String) context.get(ContextKeys.permissionGroup)
        def permissionName = (String) context.get(ContextKeys.permissionName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!permissionName) {
            throw new TestFailuresExceptions("No permission was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        permissions: [
                                [
                                        group: permissionGroup,
                                        name: permissionName
                                ]
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/permissions")
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

        assert parsed.permissions == [ ]
    }

    @Step
    void createDuplicatePermission(ScenarioContext context) {
        def permissionGroup = (String) context.get(ContextKeys.permissionGroup)
        def permissionName = (String) context.get(ContextKeys.permissionName)

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        group: permissionGroup,
                        name: permissionName,
                        domain: "e2e",
                        forAccounts: true,
                        forApplications: false
                ]))
                .when()
                .post("/domains/{domain}/permissions")
                .then()
                .statusCode(409)
                .extract()
    }

    @Step
    void grantNonExistingPermission(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "GRANT",
                        permissions: [
                                [
                                        group: "made up",
                                        name: "made up"
                                ]
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/permissions")
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

        assert parsed.permissions == [ ]
    }

    @Step
    void revokeNonGrantedPermission(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)
        def permissionGroup = (String) context.get(ContextKeys.permissionGroup)
        def permissionName = (String) context.get(ContextKeys.permissionName)

        if (!userAccount) {
            throw new TestFailuresExceptions("No user account was found in the scenario context")
        }

        if (!permissionName) {
            throw new TestFailuresExceptions("No permission was found in the scenario context")
        }

        given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        action: "REVOKE",
                        permissions: [
                                [
                                        group: permissionGroup,
                                        name: permissionName
                                ]
                        ]
                ]))
                .when()
                .patch("/domains/{domain}/accounts/" + userAccount.id + "/permissions")
                .then()
                .statusCode(200)
                .extract()
    }
}
