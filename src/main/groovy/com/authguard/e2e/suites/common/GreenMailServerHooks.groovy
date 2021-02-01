package com.authguard.e2e.suites.common

import com.authguard.e2e.suites.util.Logger
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import com.icegreen.greenmail.util.ServerSetupTest
import org.scenario.annotations.AfterStep
import org.scenario.annotations.AfterSuite
import org.scenario.annotations.BeforeSuite
import org.scenario.definitions.ScenarioContext

class GreenMailServerHooks {
    private GreenMail greenMail

    @BeforeSuite
    void startGreenMail(ScenarioContext context) {
        greenMail = new GreenMail(ServerSetup.verbose(new ServerSetup[]{ ServerSetupTest.SMTP, ServerSetupTest.SMTPS }))
        greenMail.start()

        Logger.get().info("Email server started, SMTP: {}, SMTPS: {}",
                greenMail.getSmtp().getPort(), greenMail.getSmtps().getPort())

        context.global().put(ContextKeys.emailServer, [
                "instance": greenMail,
                "protocols": ["smtp", "smtps"],
                "smtp": greenMail.getSmtp().getPort(),
                "smtps": greenMail.getSmtps().getPort()
        ])
    }

    @AfterSuite
    void stopGreenMail() {
        greenMail.stop()

        Logger.get().info("Email server was shut down")
    }

    @AfterStep
    void clearMessages() {
        greenMail.purgeEmailFromAllMailboxes()
    }
}
