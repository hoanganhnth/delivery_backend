package com.delivery.routing.application.api;

import com.delivery.routing.domain.EtaWindow;
import com.delivery.routing.domain.EtaWindowQuery;
import com.delivery.routing.domain.MatrixQuery;
import com.delivery.routing.domain.MatrixResult;
import com.delivery.routing.domain.RouteQuery;
import com.delivery.routing.domain.RouteResult;
import java.util.List;

/** Application boundary for routing decisions; adapters own HTTP/provider details. */
public interface RoutingPort {
    RouteResult route(RouteQuery query);
    List<MatrixResult> matrix(MatrixQuery query);
    EtaWindow etaWindow(EtaWindowQuery query);
}
