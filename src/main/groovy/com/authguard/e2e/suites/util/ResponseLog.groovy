package com.authguard.e2e.suites.util

import io.restassured.filter.Filter
import io.restassured.filter.FilterContext
import io.restassured.response.Response
import io.restassured.specification.FilterableRequestSpecification
import io.restassured.specification.FilterableResponseSpecification

import java.util.concurrent.TimeUnit

class ResponseLog implements Filter {
    @Override
    Response filter(final FilterableRequestSpecification requestSpec,
                    final FilterableResponseSpecification responseSpec, final FilterContext ctx) {
        Response response = ctx.next(requestSpec, responseSpec);

        if (responseSpec != null) {
            Logger.get().info("Request: {} {} \n{}\n{}", requestSpec.getMethod(), requestSpec.getURI(),
                    requestSpec.getHeaders(), requestSpec.getBody())
            Logger.get().info("Response: {} {}ms \n{}\n{}", response.statusLine(), response.timeIn(TimeUnit.MILLISECONDS),
                    response.headers(), response.body().asString())
        }

        return response
    }
}
