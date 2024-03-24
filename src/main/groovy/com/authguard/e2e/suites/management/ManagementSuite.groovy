package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.AdminSetup
import com.authguard.e2e.suites.common.ContextKeys
import com.authguard.e2e.suites.common.CreateAccount
import com.authguard.e2e.suites.common.CreateApplication
import com.authguard.e2e.suites.common.SetupHooks
import com.authguard.e2e.suites.util.TestsConfig
import org.scenario.annotations.SuiteDefinition
import org.scenario.definitions.Suite
import org.scenario.runners.DefaultOutputHooks

class ManagementSuite {
    @SuiteDefinition
    Suite suite() {
        return new Suite.Builder()
                .name("Management suite")
                .addToContext(ContextKeys.domain, "e2e")
                .addToContext(ContextKeys.baseUrl, TestsConfig.baseUrl())
                .addToContext(ContextKeys.key, TestsConfig.key())
                .addToContext(ContextKeys.otaUsername, TestsConfig.otaUsername())
                .addToContext(ContextKeys.otaPassword, TestsConfig.otaPassword())
                .addToContext(ContextKeys.idempotentKey, UUID.randomUUID().toString())
                .loadHooks(new DefaultOutputHooks())
                .loadHooks(new SetupHooks())
                .loadHooks(new AdminSetup())
//                .loadHooks(new GreenMailServerHooks())
                .loadScenarios(new CreateAccount())
                .loadScenarios(new CreateApplication())
                .loadScenarios(new AccountScenarios())
                .loadScenarios(new ApplicationScenarios())
                .loadScenarios(new RolesScenarios())
                .loadScenarios(new PermissionsScenarios())
                .loadScenarios(new AccessTokenScenarios())
                .loadScenarios(new SessionScenarios())
                .loadScenarios(new CredentialsScenarios())
                .loadScenarios(new AuthClientScenarios())
                .loadScenarios(new ActionTokenScenarios())
//                .loadScenarios(new OpenIDConnetScenario())
//                .loadScenarios(new OtpScenarios())
                .build()
    }
}
