package com.authguard.e2e.suites.management

import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioFlow

class RolesScenarios {

    @ScenarioDefinition
    Scenario rolesScenario() {
        return new Scenario.Builder()
                .name("Roles scenario")
                .description("Roles management scenario")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("createRole")
                        .build())
                .build()
    }

    @Step
    void createRole() {
        println("Done create role")
    }
}
