package com.delivery.simulator.application;

import com.delivery.simulator.application.api.*;
import com.delivery.simulator.application.api.SimulationPorts.*;
import com.delivery.identity.contracts.SimulationContext;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises application orchestration using ports only, without Spring composition or transport. */
class SimulationUseCasesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final Settings settings = mock(Settings.class);
    final Gateway gateway = mock(Gateway.class);
    final RunStore<Run> store = mock(RunStore.class);
    final Leases<Lease> leases = mock(Leases.class);
    final SimulationActorPoolClient actors = mock(SimulationActorPoolClient.class);
    final Journal journal = mock(Journal.class);
    final Faults faults = mock(Faults.class);
    final Observations observations = mock(Observations.class);
    final ExecutorService executor = mock(ExecutorService.class);
    final FakeTime time = new FakeTime();
    final Map<String, Object> values = new HashMap<>();
    final Set<String> triggers = new HashSet<>();
    RunState state;
    SimulationUseCases<RunState,Run,Lease> app;
    ObjectNode scenario;
    Run durable;

    static class FakeTime implements Time {
        long millis;
        public Instant now() { return Instant.EPOCH.plusMillis(millis); }
        public long currentTimeMillis() { return millis; }
        public long nanoTime() { return millis * 1_000_000L; }
        public void sleep(long duration) { millis += duration; }
    }
    @BeforeEach void setup() throws Exception {
        when(settings.isEnabled()).thenReturn(true);
        when(settings.getGatewayBaseUrl()).thenReturn("http://localhost:8080");
        when(settings.getAllowedGatewayHosts()).thenReturn(Arrays.asList(null,"LOCALHOST"));
        when(settings.getMaxShippers()).thenReturn(10);
        when(settings.getMaxOrdersPerRun()).thenReturn(3);
        when(settings.getPollIntervalMillis()).thenReturn(100);
        when(settings.getRunTimeoutSeconds()).thenReturn(2);
        when(settings.getHumanOrderTimeoutSeconds()).thenReturn(1);
        when(settings.getMovementTickSeconds()).thenReturn(1);
        scenario = mapper.createObjectNode().put("orderMode","SIMULATED_ORDER").put("cohortId", UUID.randomUUID().toString());
        scenario.putObject("customer").put("token","customer").put("principalId",11).put("lat",10).put("lng",106).put("paymentMethod","COD");
        scenario.putObject("restaurant").put("id",1).put("menuItemId",2).put("lat",10).put("lng",106).put("ownerToken","owner").put("ownerPrincipalId",12);
        scenario.putArray("shippers").addObject().put("id","s1").put("userId",13).put("principalId",13).put("token","shipper").put("initialLat",10).put("initialLng",106).put("reactionDelaySeconds",0);
        scenario.putArray("triggers"); scenario.putArray("assertions").addObject().put("expectedTerminalState","DELIVERED");
        values.put("RunId",UUID.randomUUID().toString()); values.put("CorrelationId","correlation");
        values.put("Status","RUNNING"); values.put("OrderStatus","PENDING"); values.put("DeliveryStatus","NONE");
        values.put("StartedAt",Instant.EPOCH); values.put("RawScenario",scenario);
        state = mock(RunState.class, invocation -> {
            String name=invocation.getMethod().getName(); Object[] args=invocation.getArguments();
            if (name.startsWith("get")) return values.get(name.substring(3));
            if (name.equals("setOrder") || name.equals("setDelivery")) {
                String kind=name.substring(3); values.put(kind+"Id",args[0]); values.put(kind+"Status",args[1]); return null;
            }
            if (name.startsWith("set")) { values.put(name.substring(3),args[0]); return null; }
            return switch (name) {
                case "snapshot" -> new LinkedHashMap<>(Map.of("runId",values.get("RunId"),"status",values.get("Status")));
                case "persistableScenarioJson" -> "{}";
                case "isTerminal" -> Set.of("PASSED","PARTIAL","FAILED","ABORTED").contains(values.get("Status"));
                case "isAborted" -> Boolean.TRUE.equals(values.get("Aborted"));
                case "isPaused" -> Boolean.TRUE.equals(values.get("Paused"));
                case "isActorReleaseSafe" -> values.get("DeliveryId")==null || Set.of("NONE","DELIVERED","CANCELLED","SHIPPER_NOT_FOUND").contains(values.get("DeliveryStatus"));
                case "pause" -> { values.put("Paused",true); values.put("Status","PAUSED"); yield null; }
                case "resume" -> { values.put("Paused",false); values.put("Status","RUNNING"); yield null; }
                case "abort" -> { values.put("Aborted",true); values.put("Paused",false); values.put("Status","ABORTED"); yield null; }
                case "markTriggerFired" -> triggers.add((String)args[0]);
                case "isTriggerFiredAtStage" -> triggers.stream().anyMatch(key -> key.endsWith(":"+args[0]));
                case "markOfferSeen" -> 0L;
                case "matchesAlgorithmTrace" -> Boolean.TRUE.equals(values.get("MatchesTrace"));
                default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        durable=mock(Run.class);
        when(durable.getRunId()).thenAnswer(i -> UUID.fromString(state.getRunId()));
        when(durable.getStatus()).thenReturn("RUNNING");
        when(store.create(any(),any(),any(),any(),any())).thenReturn(durable);
        when(store.findById(any())).thenReturn(Optional.of(durable));
        when(observations.compareAll(any(),any())).thenReturn(List.of(mapper.createObjectNode()));
        when(observations.delivery(any(),any(),any())).thenAnswer(i -> {
            JsonNode node=i.getArgument(0); if(node==null||node.isNull())return Optional.empty();
            return Optional.of(new DeliverySnapshot(node.path("id").asLong(),node.path("status").asText("NONE"),node.path("offered").asText(),node.path("assigned").asText()));
        });
        when(actors.bind(any(),any(),any())).thenAnswer(i -> new SimulationActorPoolClient.BoundActor(i.getArgument(0),
            new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,i.getArgument(1),i.getArgument(2),7L),"managed-token"));
        app=new SimulationUseCases<>(mapper,settings,gateway,store,leases,actors,journal,faults,observations,s -> state,time,executor);
    }
    Object invoke(String method,Object...args) { return ReflectionTestUtils.invokeMethod(app,method,args); }
    Map<String,RunState> runs() { return (Map<String,RunState>)ReflectionTestUtils.getField(app,"runs"); }
    void register() { runs().put(state.getRunId(),state); }
    void order(long id,String status) { state.setOrder(id,status); }
    void delivery(long id,String status) { state.setDelivery(id,status); }
    void executeSubmitted() { var task=org.mockito.ArgumentCaptor.forClass(Runnable.class); verify(executor).submit(task.capture()); task.getValue().run(); }
    JsonNode json(String value) throws Exception { return mapper.readTree(value); }
    void deliveredGateway() throws Exception {
        when(gateway.postWithHeaders(eq("/api/orders"),any(),any(),any(),any())).thenReturn(json("{\"id\":1,\"status\":\"PENDING\"}"));
        when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"status\":\"DELIVERED\"}"));
        when(gateway.get(eq("/api/deliveries/order/1"),any(),any())).thenReturn(json("{\"id\":2,\"status\":\"DELIVERED\"}"));
    }
    @Test void startRunsAllOrdersAndPersistsBeforeScheduling() throws Exception {
        deliveredGateway(); scenario.put("orderCount",2);
        assertThat(app.start(scenario)).containsEntry("status","RUNNING");
        var ordered=inOrder(store,executor); ordered.verify(store).create(any(),eq("STARTING"),eq(Instant.EPOCH),eq(Instant.EPOCH.plusSeconds(2)),eq("{}")); ordered.verify(store).save(durable); ordered.verify(executor).submit(any(Runnable.class));
        executeSubmitted(); assertThat(state.getStatus()).isEqualTo("PASSED"); verify(state).beginNextOrder(2); verify(state,times(2)).finishCurrentOrder(); verify(state).completeEmitters();
        verify(state).setEventObserver(any()); verify(state).setAssertionObserver(any());
        ((java.util.function.Consumer<Map<String,Object>>)values.get("EventObserver")).accept(Map.of("event","test"));
        ((java.util.function.Consumer<Map<String,Object>>)values.get("AssertionObserver")).accept(Map.of("assertion","test"));
        verify(journal,times(2)).record(eq(UUID.fromString(state.getRunId())),any());
    }
    @Test void failedDurableRegistrationReleasesBindingsAndRemovesMemory() {
        when(settings.isManagedActorPoolRequired()).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(store).save(any());
        assertThatThrownBy(() -> app.start(scenario)).hasMessage("Cannot durably register simulation run");
        assertThat(runs()).isEmpty(); verify(actors).unbind(11L,UUID.fromString(state.getRunId()),7L); verify(actors).unbind(13L,UUID.fromString(state.getRunId()),7L); verifyNoInteractions(executor,gateway);
    }
    @ParameterizedTest @ValueSource(strings={"plain","gateway","blank","null","abort"})
    void executionFailureHasStableOutcomeAndFinalizesEmitters(String type) {
        RuntimeException error=switch(type){case "gateway" -> new GatewayException(502,"GET /test","failed"); case "blank" -> new IllegalStateException(" "); case "null" -> new IllegalStateException(); default -> new IllegalStateException("plain");};
        if(type.equals("abort"))state.abort(); else when(gateway.post(any(),any(),any(),any())).thenThrow(error);
        invoke("execute",state); assertThat(state.getStatus()).isEqualTo(type.equals("abort")?"ABORTED":"FAILED"); verify(state).completeEmitters();
    }
    @Test void controlsAndCleanupAreFencedAndRepeatCleanupRetainsDurableFence() {
        register(); app.pause(state.getRunId()); assertThat(state.isPaused()).isTrue(); app.resume(state.getRunId()); assertThat(state.isPaused()).isFalse();
        assertThatThrownBy(() -> app.cleanup(state.getRunId())).isInstanceOf(IllegalStateException.class);
        app.abort(state.getRunId()); assertThat(state.isAborted()).isTrue(); app.pause(state.getRunId());app.resume(state.getRunId());app.abort(state.getRunId());
        assertThat(app.cleanup(state.getRunId())).containsEntry("cleaned",true); when(durable.getStatus()).thenReturn("ABORTED");
        assertThat(app.cleanup(state.getRunId())).containsEntry("idempotent",true); verifyNoInteractions(gateway,actors,leases);
        assertThatThrownBy(() -> app.cleanup("invalid")).isInstanceOf(IllegalArgumentException.class);
        when(store.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> app.cleanup(state.getRunId())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void durableQueriesOrphansAndExpiryUsePorts() {
        when(store.findAll()).thenReturn(List.of(durable)); assertThat(app.listRuns()).hasSize(1);
        when(journal.entries(any())).thenReturn(List.of(Map.of("entry",1))); assertThat(app.journal(state.getRunId())).hasSize(1);
        assertThatThrownBy(() -> app.journal("bad")).hasMessage("Invalid simulation run id");
        when(store.findByStatusIn(any())).thenReturn(List.of(durable)); app.reconcileOrphanedRuns(); verify(leases).quarantineRun(durable.getRunId()); verify(durable).setStatus("ABORTED"); verify(store).saveAll(List.of(durable));
        when(store.findByStatusIn(any())).thenReturn(List.of()); app.reconcileOrphanedRuns();
        register(); time.millis=3000; app.abortExpiredRuns(); assertThat(state.isAborted()).isTrue(); app.abortExpiredRuns(); app.heartbeatLeases(); app.shutdown();verify(executor).shutdownNow();
        assertThatThrownBy(() -> app.snapshot("missing")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void inMemoryOnlyOptionalPortsRemainSupported() {
        app=new SimulationUseCases<>(mapper,settings,gateway,null,null,null,null,faults,observations,s -> state,time,executor);
        register(); assertThat(app.listRuns()).hasSize(1); assertThat(app.journal("anything")).isEmpty(); app.reconcileOrphanedRuns(); invoke("persistStatus",state,"RUNNING");
        app.abort(state.getRunId()); app.cleanup(state.getRunId()); assertThatThrownBy(() -> app.cleanup("missing")).isInstanceOf(IllegalArgumentException.class); app.heartbeatLeases();
    }
    @Test void observationsBufferUntilCorrelatedExpireAndDeduplicate() {
        app.recordAlgorithmTrace(null); app.recordAlgorithmTrace(mapper.createArrayNode()); app.recordAlgorithmTrace(mapper.createObjectNode());
        var trace=mapper.createObjectNode().put("eventId","event"); app.recordAlgorithmTrace(trace);
        register(); values.put("MatchesTrace",true); assertThat(app.snapshot(state.getRunId())).isNotEmpty(); verify(state).addAlgorithmTrace(trace);
        app.recordAlgorithmTrace(trace); verify(state,times(2)).addAlgorithmTrace(trace); verify(state,times(2)).addAlgorithmComparison(any()); invoke("streamState",state.getRunId());
        values.put("MatchesTrace",false);app.recordAlgorithmTrace(trace); time.millis=21*60*1000;app.recordAlgorithmTrace(mapper.createObjectNode().put("eventId","new"));
        for(int i=0;i<2050;i++)app.recordAlgorithmTrace(mapper.createObjectNode().put("eventId","e"+i));
        var pending=(Map<?,?>)ReflectionTestUtils.getField(app,"pendingAlgorithmTraces"); assertThat(pending).hasSizeLessThanOrEqualTo(2048);
    }
    @ParameterizedTest @ValueSource(strings={"customer","owner","shipper","mismatch","nonobject","unavailable"})
    void actorBindingFailuresKeepRollbackAndOwnershipChecks(String type) {
        when(settings.isManagedActorPoolRequired()).thenReturn(true); scenario.withObject("restaurant").put("autoConfirm",true);
        switch(type){case "customer" -> scenario.withObject("customer").put("principalId",0);case "owner" -> scenario.withObject("restaurant").put("ownerPrincipalId",0);case "shipper" -> ((ObjectNode)scenario.path("shippers").get(0)).put("principalId",0);
        case "mismatch" -> doReturn(new SimulationActorPoolClient.BoundActor(11L,new SimulationContext(SimulationContext.ExecutionMode.SIMULATION,UUID.randomUUID(),UUID.randomUUID(),1L),"token")).when(actors).bind(any(),any(),any());
        case "nonobject" -> values.put("RawScenario",mapper.createArrayNode());
        case "unavailable" -> app=new SimulationUseCases<>(mapper,settings,gateway,store,leases,null,journal,faults,observations,s->state,time,executor);}
        assertThatThrownBy(() -> app.resolveManagedActors(state)).isInstanceOf(RuntimeException.class);
        if(type.equals("owner")||type.equals("shipper"))verify(actors).unbind(11L,UUID.fromString(state.getRunId()),7L);
    }
    @Test void successfulBindingAndUnbindOutageRetainEquivalence() {
        when(settings.isManagedActorPoolRequired()).thenReturn(true);scenario.withObject("restaurant").put("autoConfirm",true);
        app.resolveManagedActors(state);assertThat(scenario.path("restaurant").path("ownerToken").asText()).isEqualTo("managed-token");
        doThrow(new IllegalStateException()).when(actors).unbind(any(),any(),anyLong()); invoke("releaseManagedActors",state.getRunId()); invoke("releaseManagedActors",state.getRunId()); verify(actors,times(3)).unbind(any(),any(),anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"NONE","OFFERED","ASSIGNED","PICKED_UP","CANCELLED"})
    void finalizationCancelsOnlyUnacceptedDeliveryAndQuarantinesUnsafeActors(String status) {
        order(1,"RUNNING"); delivery(2,status);
        if(status.equals("OFFERED")||status.equals("ASSIGNED"))when(gateway.put(any(),any(),any(),any())).thenThrow(new GatewayException(502,"cancel","failure"));
        invoke("finalizeActorLifecycle",state,"customer");
        if(status.equals("NONE")||status.equals("CANCELLED"))verifyNoInteractions(gateway); else verify(state).addEvent(eq("LEASE"),any(),any(),eq("ERROR"));
    }
    @Test void quarantineRetainsFenceAndCancellationPollCanProveSafe() throws Exception {
        order(1,"PENDING");delivery(2,"OFFERED"); var lease=mock(Lease.class);when(lease.getLeaseId()).thenReturn(UUID.randomUUID());when(lease.getFencingToken()).thenReturn(9L);
        ((Map<String,List<Lease>>)ReflectionTestUtils.getField(app,"runLeases")).put(state.getRunId(),List.of(lease));
        invoke("quarantineRunActors",state);verify(leases).quarantine(lease.getLeaseId(),9L);
        when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"status\":\"CANCELLED\"}"));
        when(gateway.get(eq("/api/deliveries/order/1"),any(),any())).thenReturn(json("{\"id\":2,\"status\":\"CANCELLED\"}"));
        invoke("finalizeActorLifecycle",state,"customer"); assertThat(state.getDeliveryStatus()).isEqualTo("CANCELLED");
    }
    @ParameterizedTest @ValueSource(strings={"AUTO_ACCEPT","REJECT_AFTER_DELAY","TIMEOUT_IGNORE","CANCEL_AFTER_ACCEPT"})
    void singleAndBatchOfferActionsPreserveBehaviorAndDeduplication(String behavior) throws Exception {
        order(1,"WAIT_SHIPPER_CONFIRM");var shipper=(ObjectNode)scenario.path("shippers").get(0);shipper.put("behavior",behavior);
        when(gateway.get(eq("/api/deliveries/offers/current"),any(),any())).thenReturn(json("{\"orderId\":1,\"deliveryId\":2,\"expiresAt\":\"later\"}"));
        invoke("inspectOffers",state);invoke("inspectOffers",state);
        if(behavior.equals("TIMEOUT_IGNORE"))verify(gateway,never()).post(any(),any(),any(),any());else verify(gateway).post(eq("/api/deliveries/accept"),eq("shipper"),argThat(n -> n.path("action").asText().equals(behavior.equals("REJECT_AFTER_DELAY")?"REJECT":"ACCEPT")),any());
        when(gateway.get(eq("/api/deliveries/offers/current-batch"),any(),any())).thenReturn(json("{\"batchId\":\"b\",\"offers\":[{\"orderId\":1}]}"));
        invoke("inspectOffers",state);invoke("inspectOffers",state);
        if(!behavior.equals("TIMEOUT_IGNORE"))verify(gateway).post(eq(behavior.equals("REJECT_AFTER_DELAY")?"/api/deliveries/batch/reject":"/api/deliveries/batch/accept"),eq("shipper"),any(),any());
    }
    @ParameterizedTest @CsvSource({"batch,404,false","batch,204,false","batch,429,false","batch,401,true","single,404,false","single,204,false","single,429,false","single,401,true"})
    void offerTransportFailuresAreClassified(String source,int status,boolean throwsError) {
        order(1,"RUNNING"); when(gateway.get(eq("/api/deliveries/offers/current"+(source.equals("batch")?"-batch":"")),any(),any())).thenThrow(new GatewayException(status,"offer","error"));
        if(throwsError)assertThatThrownBy(() -> invoke("inspectOffers",state)).isInstanceOf(GatewayException.class);else invoke("inspectOffers",state);
    }
    @Test void offerMalformedResponsesDelaysAndOrderMembershipAreHandled() throws Exception {
        order(1,"RUNNING");
        for(String response:List.of("null","[]","{}","{\"orderId\":99}")){when(gateway.get(eq("/api/deliveries/offers/current"),any(),any())).thenReturn(json(response));invoke("inspectOffers",state);}
        var shipper=(ObjectNode)scenario.path("shippers").get(0);shipper.put("reactionDelaySeconds",2);
        when(gateway.get(eq("/api/deliveries/offers/current"),any(),any())).thenReturn(json("{\"orderId\":1}"));invoke("inspectOffers",state);
        when(gateway.get(eq("/api/deliveries/offers/current-batch"),any(),any())).thenReturn(json("{\"batchId\":\"b\",\"offers\":[{\"orderId\":1}]}"));invoke("inspectOffers",state);verify(gateway,never()).post(any(),any(),any(),any());
        assertThat(invoke("batchContainsOrder",json("{}"),(Long)null)).isEqualTo(false);assertThat(invoke("batchContainsOrder",json("{}"),0L)).isEqualTo(false);assertThat(invoke("batchContainsOrder",json("{\"offers\":[{\"orderId\":9}]}"),1L)).isEqualTo(false);
    }
    @ParameterizedTest @ValueSource(strings={"ASSIGNED","PICKED_UP","DELIVERING","CANCEL_AFTER_ACCEPT","missing"})
    void assignedFlowMovesAndTransitionsThroughPorts(String stage) {
        order(1,"RUNNING");delivery(2,stage);state.setAssignedShipperId(stage.equals("missing")?"missing":"s1");
        if(stage.equals("CANCEL_AFTER_ACCEPT"))((ObjectNode)scenario.path("shippers").get(0)).put("behavior",stage);
        if(stage.equals("DELIVERING"))invoke("completeDelivery",state);else if(stage.equals("PICKED_UP"))invoke("finishAssignedDelivery",state);else invoke("handleAssigned",state,"customer","owner");
        if(stage.equals("missing"))verifyNoInteractions(gateway);else if(stage.equals("CANCEL_AFTER_ACCEPT"))verify(gateway).post(eq("/api/deliveries/cancel-assignment"),any(),any(),any());else assertThat(state.getDeliveryStatus()).isEqualTo("DELIVERED");
    }
    @Test void noAssignmentOrDeliverySkipsWritesAndAbortFencesLocation() {
        invoke("handleAssigned",state,"customer","owner");invoke("finishAssignedDelivery",state);invoke("completeDelivery",state);invoke("transitionDelivery",state,"shipper","DELIVERED");verifyNoInteractions(gateway);
        state.abort();assertThatThrownBy(() -> invoke("updateLocation",state,"s1","shipper",10d,106d,true)).isInstanceOf(RuntimeException.class);verifyNoInteractions(gateway);
    }
    @ParameterizedTest @ValueSource(ints={429,401})
    void locationRetriesAreBoundedAndPropagateBusinessFailures(int status) {
        when(gateway.post(any(),any(),any(),any())).thenThrow(new GatewayException(status,"location","failure"));
        assertThatThrownBy(() -> invoke("updateLocation",state,"s1","shipper",10d,106d,false)).isInstanceOf(GatewayException.class);
        verify(gateway,times(status==429?9:1)).post(any(),any(),any(),any());
    }
    @Test void pollHandlesEmptyAbsentAndAliasedObservations() throws Exception {
        invoke("pollState",state,"customer");verifyNoInteractions(gateway);order(1,"PENDING");
        when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"data\":{\"status\":\"CONFIRMED\"}}"));
        when(gateway.get(eq("/api/deliveries/order/1"),any(),any())).thenReturn(json("{\"id\":2,\"status\":\"ASSIGNED\",\"offered\":\"s1\",\"assigned\":\"s1\"}"));invoke("pollState",state,"customer");assertThat(state.getAssignedShipperId()).isEqualTo("s1");verify(state).setActiveOfferShipperId("s1");
    }
    @ParameterizedTest @CsvSource({"order,429,false","order,404,true","delivery,429,false","delivery,404,false","delivery,500,true"})
    void pollingFailuresKeepExistingProjection(String source,int status,boolean fails) throws Exception {
        order(1,"PENDING");when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"status\":\"PENDING\"}"));
        when(gateway.get(eq(source.equals("order")?"/api/orders/1":"/api/deliveries/order/1"),any(),any())).thenThrow(new GatewayException(status,"poll","failure"));
        if(fails)assertThatThrownBy(() -> invoke("pollState",state,"customer")).isInstanceOf(GatewayException.class);else invoke("pollState",state,"customer");
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER_CANCEL","RESTAURANT_REJECT","SHIPPER_DISCONNECT","NETWORK_DELAY","UNKNOWN"})
    void triggersAreDeduplicatedAndFenced(String type) {
        order(1,"RUNNING");scenario.withArray("triggers").addObject().put("enabled",true).put("type",type).put("atStage","ASSIGNED").put("delaySecondsAfterStage",1);
        invoke("fireTriggers",state,"PENDING","customer","owner");invoke("fireTriggers",state,"ASSIGNED","customer","owner");invoke("fireTriggers",state,"ASSIGNED","customer","owner");
        if(type.equals("NETWORK_DELAY"))verify(faults).armOneTransientPollFailure(state.getCorrelationId());
        if(type.equals("CUSTOMER_CANCEL"))verify(gateway).put(eq("/api/orders/1/cancel"),eq("customer"),any(),any());
        if(type.equals("RESTAURANT_REJECT"))verify(gateway).post(eq("/api/restaurants/orders/1/reject"),eq("owner"),any(),any());
        if(type.equals("SHIPPER_DISCONNECT"))verify(gateway).post(eq("/api/tracking/shipper-locations/offline"),eq("shipper"),isNull(),any());
    }
    @ParameterizedTest @ValueSource(ints={400,409,500})
    void cancellationTriggerRetainsExpectedNegativePolicy(int status) {
        order(1,"RUNNING");scenario.withArray("triggers").addObject().put("enabled",true).put("type","CUSTOMER_CANCEL").put("atStage","PENDING");
        when(gateway.put(any(),any(),any(),any())).thenThrow(new GatewayException(status,"cancel","failed"));
        if(status==500)assertThatThrownBy(() -> invoke("fireTriggers",state,"PENDING","customer","owner")).isInstanceOf(GatewayException.class);else invoke("fireTriggers",state,"PENDING","customer","owner");
    }
    @ParameterizedTest @ValueSource(strings={"DELIVERED","CANCELLED","PICKED_UP","ASSIGNED","DELIVERING","FINDING_SHIPPER"})
    void deliveryLoopConvergesOrTimesOutWithCorrectActions(String stage) throws Exception {
        order(1,"RUNNING");delivery(2,stage);state.setAssignedShipperId("s1");
        when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"status\":\""+stage+"\"}"));
        when(gateway.get(eq("/api/deliveries/order/1"),any(),any())).thenReturn(json("{\"id\":2,\"status\":\""+stage+"\"}"));
        if(stage.equals("DELIVERED")||stage.equals("CANCELLED"))invoke("runDeliveryLoop",state,"customer","owner");else assertThatThrownBy(() -> invoke("runDeliveryLoop",state,"customer","owner")).hasMessageContaining("thời gian");
    }
    @Test void humanOrderDiscoveryIgnoresBaselineAndWrongRestaurant() throws Exception {
        when(gateway.get(any(),any(),any())).thenReturn(json("{\"items\":[{\"id\":1}]}"),json("{\"data\":{\"items\":[{}, {\"id\":1}, {\"id\":2,\"restaurantId\":9},{\"id\":3,\"restaurantId\":1}]}}"));
        invoke("waitForHumanOrder",state,"customer");assertThat(state.getOrderId()).isEqualTo(3L);verify(state).setStatus("RUNNING");
    }
    @Test void humanOrderTimeoutDoesNotCreateOrder() {
        assertThatThrownBy(() -> invoke("waitForHumanOrder",state,"customer")).hasMessageContaining("Hết thời gian");verify(gateway,never()).postWithHeaders(any(),any(),any(),any(),any());
    }
    @Test void assertionsPreserveLedgerSkipDefectFailurePrecedenceAndMessages() {
        scenario.withArray("assertions").removeAll();scenario.withArray("assertions").addObject().put("id","ledger").put("expectedLedgerCount",0);scenario.withArray("assertions").addObject().put("expectedTerminalState","DELIVERED");
        order(1,"CANCELLED");invoke("finishAssertions",state);assertThat(state.getStatus()).isEqualTo("FAILED");verify(state).assertion(eq("ledger"),eq("SKIPPED"),any());
        order(1,"DELIVERED");invoke("finishAssertions",state);assertThat(state.getStatus()).isEqualTo("PARTIAL");
    }
    @ParameterizedTest @ValueSource(strings={"https://production.local","http://staging.local","http://stage.local","http://prod.local","ftp://localhost","http:///invalid","http://remote.local","%"})
    void gatewayTargetSafetyPrecedesScenarioValidation(String target) {
        when(settings.getGatewayBaseUrl()).thenReturn(target);assertThatThrownBy(() -> app.validate(null)).hasMessage("SIMULATOR_GATEWAY_BASE_URL không hợp lệ hoặc không an toàn");verifyNoInteractions(gateway);
    }
    @Test void disabledAndExplicitNonLocalConfigurationAreRespected() {
        when(settings.isEnabled()).thenReturn(false);assertThatThrownBy(() -> app.start(scenario)).isInstanceOf(IllegalStateException.class);
        when(settings.isEnabled()).thenReturn(true);when(settings.isAllowNonLocalTargets()).thenReturn(true);when(settings.getGatewayBaseUrl()).thenReturn("https://dev.local");assertThat(app.validate(scenario)).containsEntry("valid",true);
    }
    @Test void validationEnumeratesInvalidFieldsWithoutSideEffects() throws Exception {
        assertThat(app.validate(null)).containsEntry("valid",false);assertThat(app.validate(mapper.createArrayNode())).containsEntry("valid",false);
        ObjectNode invalid=mapper.createObjectNode().put("orderMode","HUMAN_ORDER").put("orderCount",4);
        invalid.putObject("customer");invalid.putObject("restaurant").put("autoConfirm",true);
        invalid.putArray("shippers").addObject().put("id","duplicate").put("behavior","wrong");invalid.withArray("shippers").addObject().put("id","duplicate");
        invalid.putArray("triggers").addObject().put("type","wrong").put("atStage","wrong").put("delaySecondsAfterStage",-1);
        invalid.putArray("assertions").addObject();when(settings.getMaxShippers()).thenReturn(1);
        assertThat((List<?>)app.validate(invalid).get("errors")).hasSizeGreaterThan(10);
        when(settings.isManagedActorPoolRequired()).thenReturn(true);assertThat(app.validate(invalid)).containsEntry("valid",false);invalid.put("cohortId","bad");assertThat(app.validate(invalid)).containsEntry("valid",false);
        invalid.put("orderCount",0).put("orderMode","wrong");invalid.remove("shippers");invalid.remove("triggers");invalid.remove("assertions");assertThat(app.validate(invalid)).containsEntry("valid",false);
        assertThatThrownBy(() -> app.start(invalid)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(gateway,store,actors,executor);
    }
    @ParameterizedTest @ValueSource(strings={"confirm","trigger","no-owner","human","aborted"})
    void perOrderFlowPreservesConfirmationAndHumanOrderPolicy(String variant) throws Exception {
        scenario.withObject("restaurant").put("autoConfirm",true);
        when(gateway.postWithHeaders(eq("/api/orders"),any(),any(),any(),any())).thenReturn(json("{\"id\":1}"));
        when(gateway.get(eq("/api/orders/1"),any(),any())).thenReturn(json("{\"status\":\"PENDING\"}"),json("{\"status\":\"PENDING\"}"),json("{\"status\":\"DELIVERED\"}"));
        when(gateway.get(eq("/api/deliveries/order/1"),any(),any())).thenReturn(json("{\"id\":2,\"status\":\"NONE\"}"),json("{\"id\":2,\"status\":\"NONE\"}"),json("{\"id\":2,\"status\":\"DELIVERED\"}"));
        if(variant.equals("trigger"))scenario.withArray("triggers").addObject().put("enabled",true).put("type","NETWORK_DELAY").put("atStage","PENDING");
        if(variant.equals("human"))when(gateway.get(eq("/api/orders/my-orders?page=0&size=100"),any(),any())).thenReturn(json("{}"),json("{\"items\":[{\"id\":1,\"restaurantId\":1}]}"));
        if(variant.equals("aborted"))when(gateway.postWithHeaders(any(),any(),any(),any(),any())).thenAnswer(call -> {state.abort();return json("{\"id\":1}");});
        invoke("executeOneOrder",state,"customer",variant.equals("no-owner")?"":"owner",variant.equals("human")?"HUMAN_ORDER":"SIMULATED_ORDER");
        if(variant.equals("confirm")||variant.equals("human"))verify(gateway).post(eq("/api/restaurants/orders/1/confirm"),eq("owner"),any(),any());
        else verify(gateway,never()).post(eq("/api/restaurants/orders/1/confirm"),any(),any(),any());
    }
    @Test void quoteIsForwardedWithFreshIdempotencyKey() throws Exception {
        when(gateway.post(eq("/api/orders/checkout-preview"),any(),any(),any())).thenReturn(json("{\"data\":{\"quoteId\":\"quote\"}}"));
        when(gateway.postWithHeaders(any(),any(),any(),any(),any())).thenReturn(json("{\"id\":1}"));
        invoke("createOrder",state,"customer");
        verify(gateway).postWithHeaders(eq("/api/orders"),eq("customer"),argThat(body -> body.path("quoteId").asText().equals("quote")),any(),argThat(headers -> UUID.fromString(headers.get("Idempotency-Key"))!=null));
    }
    @Test void executorQueueAdmissionDefectRemainsUnbounded() {
        for(int index=0;index<12;index++)app.start(scenario);
        verify(executor,times(12)).submit(any(Runnable.class));
        // Admission is independent of host executor thread count; no command was rejected.
    }
    @Test void pauseAndInterruptFenceSubsequentWrites() throws Exception {
        Time controlled=mock(Time.class);app=new SimulationUseCases<>(mapper,settings,gateway,store,leases,actors,journal,faults,observations,s -> state,controlled,executor);
        state.pause();doAnswer(call -> {state.resume();return null;}).when(controlled).sleep(200);
        invoke("checkControl",state);assertThat(state.isPaused()).isFalse();
        doThrow(new InterruptedException()).when(controlled).sleep(1);
        try {assertThatThrownBy(() -> invoke("sleep",1L)).isInstanceOf(RuntimeException.class);assertThat(Thread.currentThread().isInterrupted()).isTrue();}finally{Thread.interrupted();}
        state.pause();doAnswer(call -> {state.abort();return null;}).when(controlled).sleep(200);
        invoke("updateLocation",state,"s1","shipper",10d,106d,true);
        assertThat(state.isAborted()).isTrue();
        verify(gateway).post(eq("/api/tracking/shipper-locations/update"),eq("shipper"),any(),any());
        assertThatThrownBy(() -> invoke("checkControl",state)).isInstanceOf(RuntimeException.class);
        // Existing gap: abort clears pause while sleeping, so that control check returns once.
    }

}
