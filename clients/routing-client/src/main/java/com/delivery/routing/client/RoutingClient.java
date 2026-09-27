package com.delivery.routing.client;

import com.delivery.routing.contracts.EtaWindowRequest;
import com.delivery.routing.contracts.EtaWindowResponse;
import com.delivery.routing.contracts.MatrixRequest;
import com.delivery.routing.contracts.MatrixResponse;
import com.delivery.routing.contracts.RouteRequest;
import com.delivery.routing.contracts.RouteResponse;

/** Typed access to the internal routing wire contract. */
public interface RoutingClient {

    RouteResponse getRoute(RouteRequest request);

    MatrixResponse getMatrix(MatrixRequest request);

    EtaWindowResponse getEtaWindow(EtaWindowRequest request);
}
