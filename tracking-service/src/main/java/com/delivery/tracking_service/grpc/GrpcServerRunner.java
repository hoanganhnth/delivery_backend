// package com.delivery.tracking_service.grpc;

// import io.grpc.Server;
// import io.grpc.ServerBuilder;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.stereotype.Component;

// @Component
// public class GrpcServerRunner implements CommandLineRunner {
//     private final ShipperLocationGrpcService shipperLocationGrpcService;

//     public GrpcServerRunner(ShipperLocationGrpcService shipperLocationGrpcService) {
//         this.shipperLocationGrpcService = shipperLocationGrpcService;
//     }

//     @Override
//     public void run(String... args) throws Exception {
//         Server server = ServerBuilder.forPort(9090)
//                 .addService(shipperLocationGrpcService)
//                 .build()
//                 .start();
//         System.out.println("gRPC server started on port 9090");
//         Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
//         server.awaitTermination();
//     }
// }
