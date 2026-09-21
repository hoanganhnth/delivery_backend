# Backend platform modules

This directory contains opt-in build and runtime foundations shared by backend
modules. It does not contain business rules, persistence models, service-owned
retry policy, or a universal common library.

## Build foundation canary

`delivery-platform-bom` aligns versions of the existing Delivery-owned shared
artifacts. It contributes no runtime dependency by itself and intentionally
does not import Spring Cloud dependency management.

`delivery-build-parent` inherits the existing Spring Boot 3.5.15 conventions,
uses Java 17, imports the platform BOM, and generates JaCoCo reports during the
`verify` phase. Adoption is opt-in. At this stage only `identity-contracts` uses
the parent so that the effective model can be validated before service-wide
migration.

Run the executable canary check with:

```bash
bash scripts/verify-build-foundation.sh
```

Run the migrated reactor slice directly with:

```bash
mvn -B -pl identity-contracts -am clean verify
```

The canary compares the pre-migration and current effective dependencies,
checks inherited plugin versions and executions, proves that an unexecuted
class remains visible in JaCoCo XML, checks that a library is not repackaged as
an executable Spring Boot JAR, and verifies that an unmigrated service does not
inherit JaCoCo.

This produces coverage evidence; it does not assert an overall backend coverage
percentage. The 85% line-and-branch rule will be enabled per migrated domain or
application module when those pure business modules exist. Adapter, host,
concurrency, integration, and production behavior need separate evidence.
