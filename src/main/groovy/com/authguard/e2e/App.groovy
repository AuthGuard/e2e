
package com.authguard.e2e

import org.scenario.runners.DefaultTestsRunner

class App {

    static void main(String[] args) {
        DefaultTestsRunner.main(
                "com.authguard.e2e.suites.management"
        )
    }
}
