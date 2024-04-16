package com.authguard.e2e.suites.management


import com.authguard.e2e.suites.util.Json
import org.scenario.annotations.CircuitBreaker
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

import java.time.Instant

import static io.restassured.RestAssured.given

class EventsScenarios {
    @ScenarioDefinition
    Scenario scenario() {
        return new Scenario.Builder()
                .name("OpenID Connect auth code flow")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("getAllEventsNoCursor")
                        .step("getChannelEventsNoCursor")
                        .step("getAllEventsWithCursor")
                        .build())
                .build();
    }

    @Step(description = "Get all events no cursor")
    @CircuitBreaker
    void getAllEventsNoCursor(ScenarioContext context) {
        def response = given()
                .pathParam("domain", "e2e")
                .when()
                .get("/domains/{domain}/events")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.items.size() > 0 : "No events were found"
        assert Instant.parse(parsed.items.first()["createdAt"])
                .isAfter(Instant.parse(parsed.items.last()["createdAt"])) : "Events were in the wrong order (ascending instead of ascending)"

        context.put("allEventsCount", parsed.items.size())
        context.put("allEvents", parsed.items)
    }

    @Step(description = "Get all accounts events no cursor")
    void getChannelEventsNoCursor(ScenarioContext context) {
        def allCount = context.get("allEventsCount")

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("channel", "accounts")
                .when()
                .get("/domains/{domain}/events?channel={channel}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.items.size() < allCount : "Accounts events count was not less than all count"
    }

    @Step(description = "Get all events with a cursor")
    @CircuitBreaker
    void getAllEventsWithCursor(ScenarioContext context) {
        def allEvents = (Object[]) context.get("allEvents")
        def cursor = Instant.parse(allEvents.first()["createdAt"]).toEpochMilli()

        def response = given()
                .pathParam("domain", "e2e")
                .pathParam("cursor", cursor)
                .when()
                .get("/domains/{domain}/events?cursor={cursor}")
                .then()
                .statusCode(200)
                .extract()

        def parsed = Json.slurper.parseText(response.body().asString())

        assert parsed.items.size() < allEvents.size() : "Partial events count was not less than all count"
    }
}
