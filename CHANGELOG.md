# CHANGELOG

* v6.1.0 in progress
  * Fix references to `clj-watson.properties` file in the documentation via PR [#130](https://github.com/clj-holmes/clj-watson/pull/130) from [@samumbach](https://github.com/samumbach).
  * Update dev/test dependencies to latest versions.

* v6.0.1 b520351 -- 2025-03-07
  * Fix: allow `clj-watson` to use pre-built NVD cache via PR [#127](https://github.com/clj-holmes/clj-watson/pull/127) from [@stijnopheide](https://github.com/stijnopheide).
  * Add `--cvss-fail-threshold` to fail when a vulnerability meets or exceeds a given CVSS score [#114](https://github.com/clj-holmes/clj-watson/issues/114)
  * Fix: `--output json` now renders correctly & JSON output now pretty-printed [#116](https://github.com/clj-holmes/clj-watson/issues/116)
  * Recognize CVSS2 and CVSS4 scores when available [#112](https://github.com/clj-holmes/clj-watson/issues/112)
  * Show short summary of findings [#87](https://github.com/clj-holmes/clj-watson/issues/87)
  * Bump deps [#124](https://github.com/clj-holmes/clj-watson/issues/124)

* v6.0.0 cb02879 -- 2024-08-20
  * Fix: show score and severity in dependency-check findings [#58](https://github.com/clj-holmes/clj-watson/issues/58)
  * Bump deps [#75](https://github.com/clj-holmes/clj-watson/issues/75)
  * Improve command line experience [#77](https://github.com/clj-holmes/clj-watson/issues/77)
  * Deprecate `--dependency-check-properties` command line option [#107](https://github.com/clj-holmes/clj-watson/issues/107)
  * Encourage use of NVD API key [#67](https://github.com/clj-holmes/clj-watson/issues/67)
  * Explicitly close the dependency-check engine when we are done with it [#86](https://github.com/clj-holmes/clj-watson/issues/86)
  * Respect dependency-check `odc.autoupdate` property [#88](https://github.com/clj-holmes/clj-watson/issues/88)
  * Replace deprecated clj-time dep with JDK8 java.time interop [#83](https://github.com/clj-holmes/clj-watson/issues/83)
  * Allow properties to be specified via environment variables [#104](https://github.com/clj-holmes/clj-watson/issues/104) to make it easier to use `clj-watson` in CI/CD pipelines.
  * Streamline `dependency-check.properties` file [#103](https://github.com/clj-holmes/clj-watson/issues/103) so that it only includes properties which need to be different from the defaults in the core DependencyCheck configuration.
    * This changes the default location of the local database used for analysis from `/tmp/db` to a directory within your local Maven cache (DependencyCheck's default location), which makes `clj-watson` more CI-friendly since `~/.m2` is typically cached in CI. **The first time you run `clj-watson` 6.0.0, it will download the entire NIST NVD database!**
  * Improve feedback during scan
    * Stop suppressing all logging [#68](https://github.com/clj-holmes/clj-watson/issues/68)
    * Suppress noisy INFO level logging from Apache Commons JCS [#69](https://github.com/clj-holmes/clj-watson/issues/69)
    * Suppress specific irrelevant ERROR level logging from Apache Commons JCS [#78](https://github.com/clj-holmes/clj-watson/issues/78)

* v5.1.3 5812615 -- 2024-07-31
  * Address [#60](https://github.com/clj-holmes/clj-watson/issues/60) by updating `org.owasp/dependency-check-core` to 10.0.3.

* v5.1.2 ae20e1e -- 2024-03-20
  * GitHub Advisory: fix matching CVE for allowlist via PR [#59](https://github.com/clj-holmes/clj-watson/pull/59) [@markomafs](https://github.com/markomafs).

* v5.1.1 ad5fe07 -- 2024-01-15
  * Address [#49](https://github.com/clj-holmes/clj-watson/issues/49) by improving the `-T` invocation to support short names, symbols for strings, and all the defaults.
  * Address [#48](https://github.com/clj-holmes/clj-watson/issues/48) by updating all of the project dependencies, including DependencyCheck to 9.0.8.
  * Address [#47](https://github.com/clj-holmes/clj-watson/issues/47) by printing out the optional properties read from the `clj-watson.properties` file.
  * Documentation improvements.

* v5.0.1 d1ec6e5 -- 2024-01-09
  * Fix [#44](https://github.com/clj-holmes/clj-watson/issues/44) -- locating `clj-watson.properties` file.

* v5.0.0 c2349f5 -- 2023-12-24
  * Updated to use DependencyCheck 9.0.6 (NIST NVD API)

* v4.1.3 56dfd3e -- 2023-01-24
  * Updated to use DependencyCheck 7.4.4 (NIST NVD Data Feed)

See [releases](https://github.com/clj-holmes/clj-watson/releases) for older versions.
