package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class OtpScenarios {

    @ScenarioDefinition
    Scenario otpScenarios() {
        return new Scenario.Builder()
                .name("Authenticate users with one-time passwords")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("generateOtp")
                        .step("exchangeOtpToAccessToken")
                        .build())
                .build()
    }

    @Step(description = "Generate OTP")
    @CircuitBreaker
    void generateOtp(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        def response = given()
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/auth/exchange?from=basic&to=otp")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated otp successfully {}", parsed)

        context.put(ContextKeys.token, parsed.token)
    }

    @Step(description = "Generate access token from OTP")
    void exchangeOtpToAccessToken(ScenarioContext context) {
        def otpId = context.get(ContextKeys.token)

        def scanner = new Scanner(System.in)

        System.out.print("Enter the received password: ")
        def otp = scanner.nextLine()

        def response = given()
                .body(JsonOutput.toJson([
                        passwordId: otpId,
                        password: otp
                ]))
                .when()
                .post("/otp/verify")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Refreshed access token successfully {}", parsed.token)

        context.put(ContextKeys.token, parsed.token)
        context.put(ContextKeys.oldRefreshToken, otpId)
        context.put(ContextKeys.refreshToken, parsed.refreshToken)
    }

}
