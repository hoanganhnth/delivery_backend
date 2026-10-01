#!/usr/bin/env python3
"""Require executed Phase 8 Docker suites after a fresh Maven clean verify.

This validates report content, not report freshness or runtime performance.
CI must run clean verify first so previous build reports cannot satisfy the gate.
"""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

REQUIRED_SUITES = {
    "livestream-service": (
        "com.delivery.livestream_service.checkout.LivestreamCheckoutReceiptPostgresIntegrationTest",
    ),
    "analytics-service": (
        "com.delivery.analytics_service.service.AnalyticsReceiptPostgresIntegrationTest",
    ),
    "search-service": (
        "com.delivery.search_service.consumer.SearchKafkaElasticsearchIntegrationTest",
        "com.delivery.search_service.consumer.ElasticsearchProjectionConcurrencyIntegrationTest",
    ),
    "promotion-service": (
        "com.delivery.promotion_service.listener.PromotionReservationKafkaPostgresIntegrationTest",
        "com.delivery.promotion_service.service.VoucherReservationPostgresConcurrencyTest",
        "com.delivery.promotion_service.service.PromotionOrderReservationReceiptPostgresConcurrencyTest",
    ),
    "flashsale-service": (
        "com.delivery.flashsale_service.listener.FlashSaleReservationKafkaPostgresIntegrationTest",
        "com.delivery.flashsale_service.service.FlashSaleOrderReservationReceiptPostgresConcurrencyTest",
        "com.delivery.flashsale_service.service.FlashSaleReservationPostgresConcurrencyTest",
    ),
}


def check_report(path: Path) -> list[str]:
    if not path.is_file():
        return [f"{path}: missing report"]
    try:
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            return [f"{path}: expected a Surefire testsuite"]
        cases = list(suite.iter("testcase"))
        tests = int(suite.get("tests", "0"))
        if tests <= 0 or len(cases) != tests:
            return [f"{path}: empty or inconsistent testcase evidence"]
        errors = []
        for attribute, child in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped")):
            observed = sum(case.find(child) is not None for case in cases)
            reported = int(suite.get(attribute, "0"))
            if reported < 0:
                errors.append(f"{path}: invalid {attribute} count")
            elif observed or reported:
                errors.append(f"{path}: {attribute} (reported={reported}, observed={observed})")
        return errors
    except (OSError, ET.ParseError, ValueError) as error:
        return [f"{path}: invalid report ({error})"]


def verify(root: Path) -> list[str]:
    errors = []
    for service, suites in REQUIRED_SUITES.items():
        for suite in suites:
            errors.extend(check_report(root / service / "target/surefire-reports" / f"TEST-{suite}.xml"))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = verify(args.root.resolve())
    if errors:
        print("Phase 8 integration evidence FAILED")
        print("\n".join(f"- {error}" for error in errors))
        return 1
    count = sum(len(suites) for suites in REQUIRED_SUITES.values())
    print(f"Phase 8 integration evidence PASSED: all {count} required suites executed without skips/errors/failures.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
