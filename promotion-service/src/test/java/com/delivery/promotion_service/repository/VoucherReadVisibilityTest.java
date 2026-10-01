package com.delivery.promotion_service.repository;

import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.service.PromotionOutboxService;
import com.delivery.promotion_service.service.PromotionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class VoucherReadVisibilityTest {
    @Autowired VoucherRepository vouchers;

    @Test
    void normalListsHideDeletedRowsAndLockedLookupRetainsTheTombstone() {
        Voucher shop = vouchers.saveAndFlush(voucher("SHOP", Voucher.CreatorType.SHOP, false));
        Voucher deletedShop = vouchers.saveAndFlush(voucher("DELETED_SHOP", Voucher.CreatorType.SHOP, true));
        Voucher merchant = vouchers.saveAndFlush(voucher("MERCHANT", Voucher.CreatorType.MERCHANT, false));
        vouchers.saveAndFlush(voucher("DELETED_MERCHANT", Voucher.CreatorType.MERCHANT, true));
        PromotionService service = new PromotionService(vouchers, mock(UserVoucherRepository.class),
                mock(VoucherGroupRepository.class), mock(VoucherReservationRepository.class),
                mock(PromotionOutboxService.class));

        assertThat(service.listAllVouchers()).extracting(Voucher::getId)
                .containsExactlyInAnyOrder(shop.getId(), merchant.getId());
        assertThat(service.listShopVouchers(42L, 7L)).extracting(Voucher::getId).containsExactly(shop.getId());
        assertThat(service.listMerchantVouchers(7L)).extracting(Voucher::getId).containsExactly(merchant.getId());
        assertThat(service.listPendingShopVouchers()).extracting(Voucher::getId).containsExactly(shop.getId());
        var secondPage = vouchers.findByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(
                1, 1, org.springframework.data.domain.Sort.by("id")));
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getContent()).extracting(Voucher::getId).containsExactly(merchant.getId());
        assertThat(vouchers.findByIdForUpdate(deletedShop.getId())).contains(deletedShop);
    }

    private Voucher voucher(String code, Voucher.CreatorType creator, boolean deleted) {
        LocalDateTime now = LocalDateTime.now();
        return Voucher.builder().code(code).name(code).creatorType(creator).creatorId(7L)
                .ownerPrincipalId(42L).rewardType(Voucher.RewardType.FIXED).discountValue(BigDecimal.ONE)
                .scopeType(Voucher.ScopeType.SHOP).scopeRefId(9L).totalQuantity(10).usageLimitPerUser(1)
                .startTime(now.minusDays(1)).endTime(now.plusDays(1)).active(!deleted)
                .approvalStatus(creator == Voucher.CreatorType.SHOP ? "PENDING" : "APPROVED")
                .deletedAt(deleted ? now : null).build();
    }
}
