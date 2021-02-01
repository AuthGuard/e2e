package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.common.CreateAccount
import com.authguard.e2e.suites.common.GreenMailServerHooks
import com.authguard.e2e.suites.common.SetupHooks
import com.authguard.e2e.suites.util.TestsConfig
import org.scenario.annotations.SuiteDefinition
import org.scenario.definitions.Suite
import org.scenario.runners.DefaultOutputHooks

class ManagementSuite {
//    @SuiteDefinition
    Suite suite() {
        return new Suite.Builder()
                .name("Management suite")
                .addToContext(ContextKeys.baseUrl, TestsConfig.baseUrl())
                .addToContext(ContextKeys.key, TestsConfig.key())
                .addToContext(ContextKeys.idempotentKey, UUID.randomUUID().toString())
                .loadHooks(new DefaultOutputHooks())
                .loadHooks(new SetupHooks())
                .loadHooks(new GreenMailServerHooks())
                .loadScenarios(new CreateAccount())
                .loadScenarios(new AccountScenarios())
                .loadScenarios(new RolesScenarios())
                .build()
    }
}
