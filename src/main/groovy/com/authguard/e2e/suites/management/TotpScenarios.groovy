package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.util.Json
import com.authguard.e2e.suites.util.Logger
import de.taimos.totp.TOTP
import groovy.json.JsonOutput
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Hex
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import static io.restassured.RestAssured.given

class TotpScenarios {

    @ScenarioDefinition
    Scenario otpScenarios() {
        return new Scenario.Builder()
                .name("Authenticate users with time-based one-time passwords")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("generateTotpKey")
                        .step("generateTotp")
                        .step("exchangeTotpToAccessToken")
                        .step("exchangeWrongTotpToAccessToken")
                        .step("exchangeWrongTotpLinkerToAccessToken")
//                        .step("exchangeTotpToAccessTokenWithQrCode")
                        .build())
                .build()
    }

    @Step(description = "Generate TOTP key")
    @CircuitBreaker
    void generateTotpKey(ScenarioContext context) {
        def userAccount = context.global().get(ContextKeys.createdAccount)

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        accountId: userAccount.id
                ]))
                .when()
                .post("/domains/{domain}/totp/generate")
                .then()
                .statusCode(201)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated otp successfully {}", parsed)

        context.put(ContextKeys.key, parsed.key)

        assert parsed.qrCode != null : "QR code was missing"
        saveQrCode(parsed.qrCode, "QRCode.png", 400, 400)
    }

    @Step(description = "Generate OTP")
    @CircuitBreaker
    void generateTotp(ScenarioContext context) {
        def identifiers = (List) context.global().get(ContextKeys.accountIdentifiers)
        def password = context.global().get(ContextKeys.accountPassword)

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        identifier: identifiers[0].identifier,
                        password: password,
                        domain: "e2e"
                ]))
                .when()
                .post("/domains/{domain}/auth/exchange?from=basic&to=totp")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Generated otp successfully {}", parsed)

        context.put(ContextKeys.token, parsed.token)
    }

    @Step(description = "Generate access token from TOTP (without QR code)")
    void exchangeTotpToAccessToken(ScenarioContext context) {
        def totpLinker = context.get(ContextKeys.token)
        def base32 = new Base32()
        def totpKey = base32.decode((String) context.get(ContextKeys.key))

        def hexKey = Hex.encodeHexString(totpKey);
        def totp = TOTP.getOTP(hexKey);

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        domain: "e2e",
                        token: totpLinker + ":" + totp
                ]))
                .when()
                .post("/domains/{domain}/auth/exchange?from=totp&to=accessToken")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created access token successfully {}", parsed.token)
    }

    @Step(description = "Generate access token from invalid TOTP (without QR code)")
    void exchangeWrongTotpToAccessToken(ScenarioContext context) {
        def totpLinker = context.get(ContextKeys.token)
        def totp = "1111"

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        domain: "e2e",
                        token: totpLinker + ":" + totp
                ]))
                .when()
                .post("/domains/{domain}/auth/exchange?from=totp&to=accessToken")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "TP.022" // invalid token
    }

    @Step(description = "Generate access token from invalid TOTP linker (without QR code)")
    void exchangeWrongTotpLinkerToAccessToken(ScenarioContext context) {
        def totpLinker = "invalid"
        def base32 = new Base32()
        def totpKey = base32.decode((String) context.get(ContextKeys.key))

        def hexKey = Hex.encodeHexString(totpKey);
        def totp = TOTP.getOTP(hexKey);

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        domain: "e2e",
                        token: totpLinker + ":" + totp
                ]))
                .when()
                .post("/domains/{domain}/auth/exchange?from=totp&to=accessToken")
                .then()
                .statusCode(400)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.code == "TK.022" // invalid token
    }

    @Step(description = "Generate access token from TOTP")
    void exchangeTotpToAccessTokenWithQrCode(ScenarioContext context) {
        def totpLinker = context.get(ContextKeys.token)
        def scanner = new Scanner(System.in)

        System.out.print("Enter the authenticator password (scan QRCode.png) : ")
        def totp = scanner.nextLine()

        def response = given()
                .pathParam("domain", "e2e")
                .body(JsonOutput.toJson([
                        domain: "e2e",
                        token: totpLinker + ":" + totp
                ]))
                .when()
                .post("/domains/{domain}/auth/exchange?from=totp&to=accessToken")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        Logger.get().info("Created access token successfully {}", parsed.token)
    }

    void saveQrCode(String barCodeData, String filePath, int height, int width)
            throws WriterException, IOException {
        BitMatrix matrix = new MultiFormatWriter().encode(barCodeData, BarcodeFormat.QR_CODE, width, height)
        try (FileOutputStream out = new FileOutputStream(filePath)) {
            MatrixToImageWriter.writeToStream(matrix, "png", out)
        }
    }
}
