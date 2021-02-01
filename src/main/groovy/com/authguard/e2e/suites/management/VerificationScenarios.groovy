package com.authguard.e2e.suites.management

import com.authguard.e2e.suites.common.ContextKeys
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import org.scenario.annotations.ScenarioDefinition
import org.scenario.annotations.Step
import org.scenario.definitions.Scenario
import org.scenario.definitions.ScenarioContext
import org.scenario.definitions.ScenarioFlow

class VerificationScenarios {

    @ScenarioDefinition
    Scenario accountManagement() {
        return new Scenario.Builder()
                .name("Email verification")
                .flow(new ScenarioFlow.Builder()
                        .instance(this)
                        .step("verifyEmail")
                        .build())
                .build()
    }

    @Step(name = "Verify email of the created account")
    void verifyEmail(ScenarioContext context) {
        def account = context.global().get(ContextKeys.createdAccount)
        def emailServer = (GreenMail) context.global().get(ContextKeys.emailServer).instance

        if (!emailServer) {
            throw new IllegalStateException("No email server instance was found in the context")
        }

        def didReceive = emailServer.waitForIncomingEmail(100000, 1)

        assert didReceive : "No emails were received by the server"

        def email = emailServer.getReceivedMessages()[0]

        def body = GreenMailUtil.getBody(email)

        println body

        // TODO get the token and verify the email
    }
}
