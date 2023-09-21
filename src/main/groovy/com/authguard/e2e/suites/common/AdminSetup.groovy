package com.authguard.e2e.suites.common

import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.ResponseLog
import groovy.json.JsonOutput
import io.restassured.RestAssured
import io.restassured.filter.Filter
import io.restassured.filter.FilterContext
import io.restassured.response.Response
import io.restassured.specification.FilterableRequestSpecification
import io.restassured.specification.FilterableResponseSpecification
import org.apache.commons.lang3.RandomStringUtils
import org.scenario.annotations.BeforeSuite
import org.scenario.annotations.Name
import org.scenario.definitions.ScenarioContext
import org.scenario.exceptions.TestFailuresExceptions

import static io.restassured.RestAssured.given

class AdminSetup {

    String basicAuth(String username, String password) {
        return Base64.getEncoder().encodeToString(new String(username + ":" + password).bytes)
    }

    @BeforeSuite(description = "Create an admin account and generate an API key")
    void generateKey(ScenarioContext context,
                     @Name(ContextKeys.otaUsername) String otaUsername,
                     @Name(ContextKeys.otaPassword) String otaPassword) {
        def idempotentKey = RandomStringUtils.randomAlphanumeric(10)
        def authHeader = "Basic " + basicAuth(otaUsername, otaPassword)

        createAccount(context, "global", idempotentKey, authHeader)
//        createCredentials(context, "global", idempotentKey, authHeader)
        createClient(context, idempotentKey)

        def key = createApiKey(context)

        createRestAssuredInterceptor(key)
    }

    private void createAccount(ScenarioContext context, String domain,
                               String idempotentKey, String authHeader) {
        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .header(Headers.authorization, authHeader)
                .body(JsonOutput.toJson([
                        externalId: "external",
                        roles     : ["authguard_admin"],
                        identifiers   : [
                                [
                                        "identifier": "test_admin",
                                        "type"      : "USERNAME"
                                ]
                        ],
                        plainPassword: "Admin_password",
                        domain    : domain
                ]))
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created admin account successfully {}", parsed.id)

        context.global().put(ContextKeys.adminAccount, parsed)
        context.global().put(ContextKeys.adminUsername, "test_admin")
        context.global().put(ContextKeys.adminPassword, "Admin_password")
    }

    private void createCredentials(ScenarioContext context, String domain,
                                   String idempotentKey, String authHeader) {
        def adminAccount = context.global().get(ContextKeys.adminAccount)

        if (!adminAccount) {
            throw new TestFailuresExceptions("No admin account was found in the global context")
        }

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .header(Headers.authorization, authHeader)
                .body(JsonOutput.toJson([
                        accountId     : adminAccount.id,
                        identifiers   : [
                                [
                                        "identifier": "test_admin",
                                        "type"      : "USERNAME"
                                ]
                        ],
                        plainPassword: "Admin_password",
                        domain: domain
                ]))
                .when()
                .post("/credentials")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created credentials successfully {}", parsed.id)

        context.global().put(ContextKeys.adminUsername, "test_admin")
        context.global().put(ContextKeys.adminPassword, "Admin_password")
    }

    private void createClient(ScenarioContext context, String idempotentKey) {
        def adminAccount = context.global().get(ContextKeys.adminAccount)
        def adminUsername = (String) context.global().get(ContextKeys.adminUsername)
        def adminPassword = (String) context.global().get(ContextKeys.adminPassword)

        if (!adminAccount || !adminUsername || !adminPassword) {
            throw new TestFailuresExceptions("No admin account, username, or password was found in the global context")
        }

        def authHeader = "Basic " + basicAuth(adminUsername, adminPassword)

        def response = given()
                .header(Headers.idempotentKey, idempotentKey)
                .header(Headers.authorization, authHeader)
                .body(JsonOutput.toJson([
                        name: "AuthGuard E2E",
                        accountId: adminAccount.id,
                        clientType: "ADMIN",
                        domain: "global"
                ]))
                .when()
                .post("/clients")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created admin application successfully {}", parsed.id)

        context.global().put(ContextKeys.adminApp, parsed)
    }

    private String createApiKey(ScenarioContext context) {
        def adminApp = context.global().get(ContextKeys.adminApp)
        def adminUsername = (String) context.global().get(ContextKeys.adminUsername)
        def adminPassword = (String) context.global().get(ContextKeys.adminPassword)

        if (!adminApp) {
            throw new TestFailuresExceptions("No admin application was found in the global context")
        }

        def authHeader = "Basic " + basicAuth(adminUsername, adminPassword)

        def response = given()
                .header(Headers.authorization, authHeader)
                .body(JsonOutput.toJson([
                        forClient: true,
                        appId: adminApp.id,
                        keyType: "default"
                ]))
                .when()
                .post("/keys")
                .then()
                //.statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated API key successfully {}", parsed.key)

        return parsed.key
    }

    private void createRestAssuredInterceptor(String apiKey) {
        RestAssured.filters(new ResponseLog(),
                new Filter() {
                    @Override
                    Response filter(final FilterableRequestSpecification requestSpec, final FilterableResponseSpecification responseSpec, final FilterContext ctx) {
                        if (requestSpec != null) {
                            def headers = requestSpec.headers
                            def authorization = headers.get(Headers.authorization)

                            if (authorization == null || authorization.value.isBlank()) {
                                requestSpec.header(Headers.authorization, "Bearer " + apiKey)
                            }
                        }

                        return ctx.next(requestSpec, responseSpec);
                    }
                }
        )
    }
}
