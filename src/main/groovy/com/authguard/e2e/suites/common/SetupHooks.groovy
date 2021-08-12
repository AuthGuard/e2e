package com.authguard.e2e.suites.common

import com.authguard.e2e.suites.util.Headers
import com.authguard.e2e.suites.util.ResponseLog
import io.restassured.RestAssured
import io.restassured.filter.Filter
import io.restassured.filter.FilterContext
import io.restassured.response.Response
import io.restassured.specification.FilterableRequestSpecification
import io.restassured.specification.FilterableResponseSpecification
import org.scenario.annotations.BeforeSuite
import org.scenario.annotations.Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SetupHooks {
    private final static Logger log = LoggerFactory.getLogger("hooks")

    @BeforeSuite(description = "Set the base URL")
    void configureRestAssured(@Name(ContextKeys.baseUrl) String baseUrl,
                              @Name(ContextKeys.key) String apiKey) {
        log.info("Set base URL to {}", baseUrl)

        RestAssured.baseURI = baseUrl

//        RestAssured.filters(new ResponseLog(),
//                new Filter() {
//                    @Override
//                    Response filter(final FilterableRequestSpecification requestSpec, final FilterableResponseSpecification responseSpec, final FilterContext ctx) {
//                        if (requestSpec != null) {
//                            requestSpec.header(Headers.authorization, "Bearer " + apiKey)
//                        }
//
//                        return ctx.next(requestSpec, responseSpec);
//                    }
//                }
//        )
    }
}
