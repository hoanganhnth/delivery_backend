package com.delivery.routing.application.api;

import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import java.util.List;

/** Provider boundary; adapters own HTTP, credentials and provider response parsing. */
public interface RoutingProviderPort {
    RouteResult route(RouteQuery query);

    List<MatrixResult> matrix(MatrixQuery query);
}
