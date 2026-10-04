package com.delivery.settlement_service.payment;

import com.delivery.settlement.application.api.*;
import com.delivery.settlement_service.SettlementServiceApplication;
import com.delivery.settlement_service.controller.PaymentController;
import com.delivery.settlement_service.entity.*;
import com.delivery.settlement_service.repository.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Processing alone enables the controller/core, independently of the application-api gate.
 * Security filters are excluded to isolate IPN mapping; real VNPay HMAC verification is retained.
 * VNPay creation only signs a URL, so no provider HTTP stub is needed. */
@SpringBootTest(classes = SettlementServiceApplication.class, properties = {
        "app.payment.processing-enabled=true", "app.settlement.application-api-enabled=false",
        "payment.vnpay.tmn-code=test-merchant", "payment.vnpay.hash-secret=ipn-proof-secret",
        "spring.task.scheduling.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@EmbeddedKafka(partitions = 1, topics = {"payment.completed", "payment.failed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentSignedIpnIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired PaymentController controller;
    @Autowired PaymentWorkflowPort workflow;
    @Autowired PaymentOrderRepository payments;
    @Autowired TransactionRepository transactions;
    @Autowired BalanceRepository balances;
    @BeforeEach void clean() { payments.deleteAll(); transactions.deleteAll(); balances.deleteAll(); }
    @AfterEach void cleanup() { clean(); }
    private PaymentWorkflowResult create() {
        return workflow.create(new PaymentWorkflowCommand(44L, 91L, "SHIPPER", new BigDecimal("100"),
                "VNPAY", "DEPOSIT_TOPUP", null, null));
    }
    static Map<String, String> signed(String ref, String amount) throws Exception {
        var params = new TreeMap<String, String>();
        params.put("vnp_TxnRef", ref); params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionNo", "provider-tx");
        var encoded = new ArrayList<String>();
        params.forEach((key, value) -> encoded.add(URLEncoder.encode(key, StandardCharsets.US_ASCII)
                + "=" + URLEncoder.encode(value, StandardCharsets.US_ASCII)));
        var mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("ipn-proof-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        params.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(String.join("&", encoded).getBytes(StandardCharsets.UTF_8))));
        return params;
    }
    private void ipn(Map<String, String> params, String code) throws Exception {
        var request = get("/api/settlement/payments/vnpay-ipn");
        params.forEach(request::param);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value(code));
    }
    @Test void processingFlagAloneStartsFullApplicationAndSignedSuccessReplaysWithoutFinancialEffects() throws Exception {
        assertThat(controller).isNotNull();
        assertThat(context.getBeansOfType(PaymentUseCase.class)).isEmpty();
        var created = create(); var params = signed(created.paymentReference(), "10000");
        ipn(params, "00"); ipn(params, "00");
        var saved = payments.findById(created.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentOrder.PaymentStatus.SUCCESS);
        assertThat(saved.getProviderTransactionId()).isEqualTo("provider-tx");
        assertThat(saved.getCallbackPayload()).contains("vnp_SecureHash", created.paymentReference());
        assertThat(saved.getSettlementTransactionId()).isNotNull();
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(balances.findByEntityIdAndEntityType(44L, EntityType.SHIPPER).orElseThrow().getDepositBalance())
                .isEqualByComparingTo("100");
    }
    @Test void signedMissingPaymentReturns99AndCreatesNothing() throws Exception {
        ipn(signed("missing", "10000"), "99");
        assertThat(payments.count()).isZero(); assertThat(transactions.count()).isZero(); assertThat(balances.count()).isZero();
    }
    @Test void invalidSignatureReturns97AndLeavesOriginalPendingState() throws Exception {
        var created = create(); var params = signed(created.paymentReference(), "10000");
        params.put("vnp_SecureHash", "invalid"); ipn(params, "97"); assertPending(created);
    }
    @Test void signedAmountMismatchReturns97AndRollsBackFailureMetadata() throws Exception {
        var created = create(); ipn(signed(created.paymentReference(), "9999"), "97"); assertPending(created);
    }
    private void assertPending(PaymentWorkflowResult created) {
        var saved = payments.findById(created.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentOrder.PaymentStatus.PENDING);
        assertThat(saved.getCallbackPayload()).isNull();
        assertThat(saved.getProviderTransactionId()).isEqualTo(created.providerTransactionId());
        assertThat(saved.getSettlementTransactionId()).isNull();
        assertThat(transactions.count()).isZero(); assertThat(balances.count()).isZero();
    }
}
