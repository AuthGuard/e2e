package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import com.authguard.e2e.suites.util.ResponseAssertions
import groovy.json.JsonOutput
import org.apache.commons.lang3.RandomStringUtils
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class OpenIDConnetScenario {
    @ScenarioDefinition
    Scenario openIdConnectAuthCodeFlow() {
        return new Scenario.Builder()
                .name("OpenID Connect auth code flow")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createSsoClient")
                        .step("createApiKey")
                        .step("getLoginPage")
                        .step("getAuthorizationCode")
                        .step("exchangeAuthorizationCode")
                        .step("refresh")
                        .step("getAuthorizationCodeInvalidClient")
                        .step("getLoginPagePkce")
                        .step("getAuthorizationCodePkce")
                        .step("exchangeAuthorizationCodePkce")
                        .step("exchangeAuthorizationCodePkceInvalidVerifier")
                        .step("refresh")
                        .build())
                .build();
    }

    @Step(description = "Create SSO client")
    @CircuitBreaker
    void createSsoClient(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.idempotentKey, UUID.randomUUID().toString())
                .body(JsonOutput.toJson([
                        name: "Test SSO app",
                        clientType: "SSO",
                        domain: "e2e",
                        baseUrl: "test-server.com"
                ]))
                .when()
                .post("/domains/{domain}/clients")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created SSO app successfully {}", parsed.id)

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

        Logger.get().info("Generated SSO API key successfully {}", parsed.key)

        context.put(ContextKeys.key, parsed.key)
    }

    @Step(description = "Get login page")
    @CircuitBreaker
    void getLoginPage(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def state = RandomStringUtils.randomAlphanumeric(5)
        def url = String.format("/oidc/e2e/auth?client_id=%s&scope=oidc&" +
                "response_type=code&" +
                "redirect_uri=http://test-server.com/handler&state=%s", app.id, state)

        def response = given()
                .header(Headers.anonymous, 1)
                .when()
                .redirects().follow(false)
                .get(url)
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def redirectUrl = response.header(Headers.httpLocation)
        def regex = ".*/oidc/e2e/login\\?redirect_uri=http://test-server.com/handler&token=.+"

        assert redirectUrl.matches(regex) : "Redirect URL '" + redirectUrl + "' doesn't match the expected regex"

        def token = redirectUrl.substring(redirectUrl.indexOf("token=") + 6)

        assert token != null : "Token in URL was null"

        context.put(ContextKeys.authorizationCodeRequestToken, token)
    }

    @Step(description = "Get authorization code")
    @CircuitBreaker
    void getAuthorizationCode(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)
        def token = context.get(ContextKeys.authorizationCodeRequestToken)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .body(JsonOutput.toJson([
                        requestToken: token,
                        redirectUri: "http://test-server.com/handler",
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/oidc/{domain}/auth")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def redirectUrl = response.header(Headers.httpLocation)
        def regex = "http://test-server.com/handler\\?code=.+&state=.+"

        assert redirectUrl.matches(regex) : "Redirect URL '" + redirectUrl + "' doesn't match the expected regex"

        def authorizationCode = redirectUrl.substring(redirectUrl.indexOf("code=") + 5, redirectUrl.indexOf("&", redirectUrl.indexOf("code=")))

        context.put(ContextKeys.authorizationCode, authorizationCode)
    }

    @Step(description = "Get authorization code (invalid client)")
    void getAuthorizationCodeInvalidClient(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        // step 1: get the login page to create the token for this request
        def state = RandomStringUtils.randomAlphanumeric(5)
        def url = String.format("/oidc/e2e/auth?client_id=%s&scope=oidc&" +
                "response_type=code&" +
                "redirect_uri=http://test-server.com/handler&state=%s", "5", state)

        def response = given()
                .header(Headers.anonymous, 1)
                .when()
                .redirects().follow(false)
                .get(url)
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def pageRedirectUrl = response.header(Headers.httpLocation)
        def regex = ".*/oidc/e2e/login\\?redirect_uri=http://test-server.com/handler&token=.+"

        assert pageRedirectUrl.matches(regex) : "Redirect URL '" + pageRedirectUrl + "' doesn't match the expected regex"

        def token = pageRedirectUrl.substring(pageRedirectUrl.indexOf("token=") + 6)

        // step 2: use the token to create the request
        response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .body(JsonOutput.toJson([
                        requestToken: token,
                        redirectUri: "localhost:7000/handler",
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/oidc/{domain}/auth")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def authRedirectUrl = response.header(Headers.httpLocation)
        def expected = "localhost:7000/handler?error=unauthorized_client"

        assert authRedirectUrl == expected : "Redirect URL '" + authRedirectUrl + "' doesn't match expected '" + expected + "'"
    }

    @Step(description = "Get login page (PKCE)")
    @CircuitBreaker
    void getLoginPagePkce(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def state = RandomStringUtils.randomAlphanumeric(5)
        // the SHA-256 of totally_random_plain
        def codeChallenge = "17f40d40a02cc59818931d1ffb2415c75357977ee43719a8ef83fa9f807e262b"
        def url = String.format("/oidc/e2e/auth?client_id=%s&scope=oidc&" +
                "response_type=code&" +
                "redirect_uri=http://test-server.com/handler&state=%s&code_challenge=%s&code_challenge_method=%s",
                app.id, state, codeChallenge, "S256")

        def response = given()
                .header(Headers.anonymous, 1)
                .when()
                .redirects().follow(false)
                .get(url)
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def redirectUrl = response.header(Headers.httpLocation)
        def regex = ".*/oidc/e2e/login\\?redirect_uri=http://test-server.com/handler&token=.+"

        assert redirectUrl.matches(regex) : "Redirect URL '" + redirectUrl + "' doesn't match the expected regex"

        def token = redirectUrl.substring(redirectUrl.indexOf("token=") + 6)

        assert token != null : "Token in URL was null"

        context.put(ContextKeys.authorizationCodeRequestToken, token)
    }

    @Step(description = "Get authorization code (PKCE)")
    @CircuitBreaker
    void getAuthorizationCodePkce(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)
        def token = context.get(ContextKeys.authorizationCodeRequestToken)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .body(JsonOutput.toJson([
                        clientId: app.id,
                        requestToken: token,
                        redirectUri: "http://test-server.com/handler",
                        identifier: identifiers[0].identifier,
                        password: password
                ]))
                .when()
                .post("/oidc/{domain}/auth")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 302)

        def redirectUrl = response.header(Headers.httpLocation)
        def regex = "http://test-server.com/handler\\?code=.+&state=.+"

        assert redirectUrl.matches(regex) : "Redirect URL '" + redirectUrl + "' doesn't match the expected regex"

        def authorizationCode = redirectUrl.substring(redirectUrl.indexOf("code=") + 5, redirectUrl.indexOf("&", redirectUrl.indexOf("code=")))

        context.put(ContextKeys.authorizationCode, authorizationCode)
    }

    @Step(description = "Exchange authorization code")
    @CircuitBreaker
    void exchangeAuthorizationCode(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def authorizationCode = context.get(ContextKeys.authorizationCode)
        def apiKey = context.get(ContextKeys.key)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authorizationCode)
                .formParam("client_id", (String) app.id)
                .formParam("client_secret", apiKey)
                .when()
                .post("/oidc/{domain}/token")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 200)

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.access_token != null : "access_token field wasn't set"
        assert parsed.refresh_token != null : "refresh_token field wasn't set"
        assert parsed.id_token != null : "id_token field wasn't set"
        assert parsed.expires_in != null : "expires_in field wasn't set"

        Logger.get().info("Generated access token successfully {}", parsed)

        context.put(ContextKeys.token, parsed.access_token)
        context.put(ContextKeys.refreshToken, parsed.refresh_token)
    }

    @Step(description = "Exchange authorization code (PKCE)")
    @CircuitBreaker
    void exchangeAuthorizationCodePkce(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def authorizationCode = context.get(ContextKeys.authorizationCode)
        def codeVerifier = "totally_random_plain"

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authorizationCode)
                .formParam("client_id", (String) app.id)
                .formParam("code_verifier", codeVerifier)
                .when()
                .post("/oidc/{domain}/token")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 200)

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.access_token != null : "access_token field wasn't set"
        assert parsed.refresh_token != null : "refresh_token field wasn't set"
        assert parsed.id_token != null : "id_token field wasn't set"
        assert parsed.expires_in != null : "expires_in field wasn't set"

        Logger.get().info("Generated access token successfully {}", parsed)

        context.put(ContextKeys.token, parsed.access_token)
        context.put(ContextKeys.refreshToken, parsed.refresh_token)
    }

    @Step(description = "Exchange authorization code (PKCE) invalid verifier")
    @CircuitBreaker
    void exchangeAuthorizationCodePkceInvalidVerifier(ScenarioContext context) {
        def app = context.get(ContextKeys.app)
        def authorizationCode = context.get(ContextKeys.authorizationCode)
        def codeVerifier = "wrong"

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .formParam("grant_type", "authorization_code")
                .formParam("code", authorizationCode)
                .formParam("client_id", (String) app.id)
                .formParam("code_verifier", codeVerifier)
                .when()
                .post("/oidc/{domain}/token")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 400)

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.error == "AT.039" : "Wrong error code (" + parsed.code + ")"
    }

    @Step(name = "Refresh token")
    void refresh(ScenarioContext context) {
        def refreshToken = context.get(ContextKeys.refreshToken)
        def app = context.get(ContextKeys.app)
        def apiKey = context.get(ContextKeys.key)

        def response = given()
                .pathParam("domain", "e2e")
                .header(Headers.anonymous, 1)
                .formParam("grant_type", "refresh_token")
                .formParam("refresh_token", refreshToken)
                .formParam("client_id", (String) app.id)
                .formParam("client_secret", apiKey)
                .when()
                .post("/oidc/{domain}/token")
                .then()
                .extract()

        ResponseAssertions.assertStatusCode(response, 200)

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Refreshed access token successfully {}", parsed.token)

        context.put(ContextKeys.token, parsed.token)
        context.put(ContextKeys.oldRefreshToken, refreshToken)
        context.put(ContextKeys.refreshToken, parsed.refreshToken)
    }
}
