package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.util.Json
import groovy.json.JsonOutput
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import static io.restassured.RestAssured.given

class KeyManagementScenarios {
    @ScenarioDefinition
    Scenario regularKeyManagement() {
        return new Scenario.Builder()
                .name("Key management")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("generateAesKey")
                        .step("generateRsaKeys")
                        .step("generateEcKeys")
                        .step("generateAesKeyAndPersist")
                        .step("retrieveAesKey")
                        .step("retrieveAndDecryptAesKey")
                        .step("generateRsaKeyAndPersist")
                        .step("retrieveRsaKey")
                        .step("retrieveAndDecryptRsaKey")
                        .step("deleteAesKey")
                        .build())
                .build();
    }

    @ScenarioDefinition
    Scenario passcodeScenario() {
        return new Scenario.Builder()
                .name("Key management with passcode protection")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("generateAndPersistWithPasscode")
                        .step("retrieveAndDecryptWithPasscode")
                        .step("retrieveAndDecryptWithWrongPasscode")
                        .step("retrieveAndDecryptWithoutPasscode")
                        .build())
                .build();
    }

    @Step(description = "Generate AES keys")
    void generateAesKey(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "AES",
                        size: 128,
                        persist: false
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"
    }

    @Step(description = "Generate RSA keys")
    void generateRsaKeys(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "RSA",
                        size: 1024,
                        persist: false
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "RSA private key was null"
        assert parsed.publicKey != null : "RSA public key was null"
    }

    @Step(description = "Generate EC keys")
    void generateEcKeys(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "EC_SECP128K1",
                        size: 1024,
                        persist: false
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "EC private key was null"
        assert parsed.publicKey != null : "EC public key was null"
    }

    @Step(description = "Generate AES keys and persist")
    @CircuitBreaker
    void generateAesKeyAndPersist(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "AES",
                        size: 128,
                        persist: true
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.id != null : "AES key didn't have an ID"
        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"

        context.put("aesKeyId", parsed.id)
        context.put("aesKey", parsed.privateKey)
    }

    @Step(description = "Retrieve persisted AES key")
    void retrieveAesKey(ScenarioContext context) {
        def keyId = context.get("aesKeyId")
        def key = context.get("aesKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"
        assert parsed.privateKey != key : "Retrieved AES was the same as the generated one (was decrypted without setting the decrypt flag)"
    }

    @Step(description = "Retrieve and decrypt persisted AES key")
    void retrieveAndDecryptAesKey(ScenarioContext context) {
        def keyId = context.get("aesKeyId")
        def key = context.get("aesKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .queryParam("decrypt", "1")
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"
        assert parsed.privateKey == key : "Decrypted AES was different from the generated one"
    }

    @Step(description = "Generate RSA keys and persist")
    void generateRsaKeyAndPersist(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "RSA",
                        size: 1024,
                        persist: true
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.id != null : "RSA key didn't have an ID"
        assert parsed.privateKey != null : "RSA private key was null"
        assert parsed.publicKey != null : "RSA public key was null"

        context.put("rsaKeyId", parsed.id)
        context.put("rsaPrivateKey", parsed.privateKey)
        context.put("rsaPublicKey", parsed.publicKey)
    }

    @Step(description = "Retrieve persisted RSA key")
    void retrieveRsaKey(ScenarioContext context) {
        def keyId = context.get("rsaKeyId")
        def privateKey = context.get("rsaPrivateKey")
        def publicKey = context.get("rsaPublicKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "RSA private key was null"
        assert parsed.publicKey != null : "RSA public key was null"
        assert parsed.publicKey == publicKey : "Retrieved public RSA key was different from the generated one"
        assert parsed.privateKey != privateKey : "Retrieved private RSA key was the same as the generated one (was decrypted without setting the decrypt flag)"
    }

    @Step(description = "Retrieve and decrypt persisted RSA key")
    void retrieveAndDecryptRsaKey(ScenarioContext context) {
        def keyId = context.get("rsaKeyId")
        def privateKey = context.get("rsaPrivateKey")
        def publicKey = context.get("rsaPublicKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .queryParam("decrypt", "1")
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "RSA private key was null"
        assert parsed.publicKey != null : "RSA public key was null"
        assert parsed.publicKey == publicKey : "Retrieved public RSA key was different from the generated one"
        assert parsed.privateKey == privateKey : "Decrypted private RSA key was different the generated one"
    }

    @Step(description = "Delete persisted AES key")
    void deleteAesKey(ScenarioContext context) {
        def keyId = context.get("aesKeyId")

        given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .when()
                .delete("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .when()
                .get("/domains/{domain}/kms/{id}")
                .then()
                .statusCode(404)
                .extract()
    }

    @Step(description = "Generate AES keys and persist with passcode")
    @CircuitBreaker
    void generateAndPersistWithPasscode(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        algorithm: "AES",
                        size: 128,
                        persist: true,
                        passcodeProtected: true,
                        passcode: "pass"
                ]))
                .when()
                .post("/domains/{domain}/kms/generator")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.id != null : "AES key didn't have an ID"
        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"
        assert parsed.passcode == null : "Passcode was returned in response"

        context.put("aesKeyId", parsed.id)
        context.put("aesKey", parsed.privateKey)
    }

    @Step(description = "Retrieve persisted AES key with passcode")
    void retrieveAndDecryptWithPasscode(ScenarioContext context) {
        def keyId = context.get("aesKeyId")
        def key = context.get("aesKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .queryParam("decrypt", "1")
                .queryParam("passcode", "pass")
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.privateKey != null : "AES private key was null"
        assert parsed.publicKey == null : "AES public key was not null"
        assert parsed.privateKey == key : "Retrieved AES was different from the generated one"
    }

    @Step(description = "Retrieve persisted AES key with passcode")
    void retrieveAndDecryptWithWrongPasscode(ScenarioContext context) {
        def keyId = context.get("aesKeyId")
        def key = context.get("aesKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .queryParam("decrypt", "1")
                .queryParam("passcode", "wrong")
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "CR.024"
    }

    @Step(description = "Retrieve persisted AES key with passcode")
    void retrieveAndDecryptWithoutPasscode(ScenarioContext context) {
        def keyId = context.get("aesKeyId")
        def key = context.get("aesKey")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("id", keyId)
                .queryParam("decrypt", "1")
                .when()
                .get("/domains/{domain}/kms/keys/{id}")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "CR.025"
    }
}
