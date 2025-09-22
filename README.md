# `clj-watson`

A Clojure tool that checks for vulnerable dependencies

`clj-watson` is a software composition analysis (SCA) tool that:
1. scans dependencies specified in a Clojure `deps.edn` file 
2. looks for vulnerable direct and transitive dependencies
3. builds a report with all the information needed to help you understand how the vulnerabilities manifest in your software

`clj-watson` can suggest a remediation for the vulnerabilities found,
and can check against both the
[NIST National Vulnerability Database (NVD)](https://nvd.nist.gov/)
(by default) and the
[GitHub Advisory Database](https://github.com/advisories)
(experimental).

> [!IMPORTANT]
> We only support latest version of `clj-watson`.
> All earlier versions of `clj-watson` are officially deprecated.

## Quick Start

1. `clj-watson` can be added as an alias, either on a per-project basis in your
project's `deps.edn` file, or in your user `deps.edn` file
(either `~/.clojure/deps.edn` or `~/.config/clojure/deps.edn`):

    ```clojure
      ;; under :aliases
      :clj-watson {:replace-deps
                   {io.github.clj-holmes/clj-watson
                    {:git/tag "v6.0.1" :git/sha "b520351"}}
                   :main-opts ["-m" "clj-watson.cli"]}
    ```

2. [Setup your NVD API key](#nist-nvd-api).

3. Optionally [setup OSS Index enrichment](#oss-index-configuration) which is now disabled if credentials are not configured.

4. Run clj-watson like so:

      ```bash
      clojure -M:clj-watson scan -p deps.edn
      ```

The first time `clj-watson` runs, it downloads the entire vulnerability database.
This can take several minutes. Subsequent runs will be much faster.

> [!NOTE]
> The database is stored under in your local maven repository, under dependency-check-utils `~/.m2/repository/org/owasp/dependency-check-utils/12.1.6/data/11.0/`.
> If you delete this directory, the database will be automatically re-downloaded.

`clj-watson` can also be installed as a Clojure CLI tool:

```bash
clojure -Ttools install-latest :lib io.github.clj-holmes/clj-watson :as clj-watson
```

After [setting up your NVD API key](#nist-nvd-api), you can run `clj-watson` like so:

```bash
clojure -Tclj-watson scan :deps-edn-path deps.edn
```

The -T tool option keywords match both the long-form and alias [-M CLI options](#cli-options).
So this also works:

```bash
clojure -Tclj-watson scan :p deps.edn
```

> [!NOTE]
> For -T tool usage, `:aliases` (or `:a`) is specified as a vector of keywords (or symbols), e.g., `:a '[:foo :bar]'`, whereas it is specified multiple times in for -M usage, `-a foo -a bar`.
> Run:
> - `clojure -M:clj-watson scan --help` for -M usage help
> - `clojure -Tclj-watson scan :help true` for -T tool usage help

## Vulnerability Database Strategies

`clj-watson` supports two strategies for vulnerabilities scans:
- [DependencyCheck](#dependencycheck)
- [GitHub Advisory Database [experimental]](#github-advisory-database-experimental)

## DependencyCheck

[DependencyCheck](https://github.com/dependency-check/DependencyCheck) is the most
widely used method among Clojure/Java SCA tools. It:
1. Downloads a database of known vulnerabilities from [NIST NVD](https://nvd.nist.gov/), storing it locally (inside your local Maven repository, under `~/.m2/repository/org/owasp/dependency-check-utils/12.1.6/data/11.0/`).
3. Scans JARs from dependencies specified in your `deps.edn`
4. Composes a [Common Platform Enumeration (CPE)](https://nvd.nist.gov/products/cpe) based on your dependencies
5. Returns any matching vulnerabilities

`clj-watson` then reports these findings to you, optionally with [potential remediations](#remediation-suggestions).

### NIST NVD API

> [!IMPORTANT]
> The [NIST NVD data feeds discourage access without API keys by heavily throttling anonymous requests](https://nvd.nist.gov/general/news/API-Key-Announcement).
> So, request one and use it.

It is easy to [request an API key](https://github.com/dependency-check/DependencyCheck/tree/main?tab=readme-ov-file#nvd-api-key-highly-recommended).

You can specify your key via:

1. The `nvd.api.key` Java system property on the command line
2. Or, the `CLJ_WATSON_NVD_API_KEY` environment variable
3. Or, an `nvd.api.key` entry in your `clj-watson.properties` file

> [!CAUTION]
> Keeping your nvd api key secret is your responsibility.
> This is not a hugely sensitive secret, but you don't want others to use your key.
> You do not want to check it into any version control system.

### OSS Index Configuration

> [!NOTE]
> When the OSS Index started requiring authentication, DependencyCheck switched to automatically disabling its usage when credentials are not configured.
> You can re-enable it, if you so wish, by specifying OSS Index credentials.

[DependencyCheck can enrich vulnerability findings by consulting the OSS Index](https://dependency-check.github.io/DependencyCheck/analyzers/oss-index-analyzer.html).

To enable the OSS Index, you'll need [OSS Index credentials](https://ossindex.sonatype.org/user/register):
1. specify `analyzer.ossindex.user`, and `analyzer.ossindex.password` Java system properties on the command line
2. Or, specify `CLJ_WATSON_ANALYZER_OSSINDEX_USER` and `CLJ_WATSON_ANALYZER_OSSINDEX_PASSWORD` environment variables
3. Or, add `analyzer.ossindex.user` & `analayzer.ossindex.password` entries in your `clj-watson.properties` file

> [!CAUTION]
> Keeping your OSS Index credentials secret is your responsibility.
> You do not want to check them into any version control system.

> [!TIP]
> If you want to explicitly disable OSS Index enrichment and quiet the warning from DependencyCheck about missing credentials, specify:
> 1. `analyzer.ossindex.enabled=false` as a Java system property on the command line
> 2. or `CLJ_WATSON_ANALYZER_OSSINDEX_ENABLED=false` environment variable
> 3. or `analyzer.ossindex.enabled=false` in your `clj-watson.properties` file

### Specifying DependencyCheck Options
In all examples below, replace `<your ...>` with your actual values.

You can mix and match, precedence, from highest to lowest, is:

1. **Java System Properties** as specified on command line.

   Example: `-J-Dnvd.api.key=<your nvd nist api key here>`
2. **Environment Variables**
Environment variables are often the most straightforward and most secure way to provide sensitive information like API keys and credentials in CI systems.

   Example: `CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here>`

   `clj-watson` converts environment variables starting with `CLJ_WATSON_` to DependencyCheck system properties. For example `CLJ_WATSON_NVD_API_KEY` converts to the `nvd.api.key` system property.
Specify two underscores for system properties with an underscore, for example `data.file_name` is expressed as `CLJ_WATSON_DATA_FILE__NAME`.
3. **Properties File**  as specified in the `clj-watson.properties` file.

   Example: `oss.index.enabled=false`

   `clj-watson` first loads its default internal `dependency-check.properties` and applies overrides from your `clj-watson.properties` file. The `clj-watson.properties` file is specified explicitly via `--clj-watson-properties` on the [command line](#cli-options), or discovered automatically on your classpath.

#### Using Java System Property on the Command Line

Example -M usage:
```shell
clojure -J-Dnvd.api.key=<your nvd nist api key here> \
  -M:clj-watson scan -p deps.edn
```

<details>
  <summary>With OSS Index enabled:</summary>

```shell
clojure -J-Dnvd.api.key=<your nvd nist api key here> \
        -J-Danalyzer.ossindex.user=<your ossindex user here> \
        -J-Danalyzer.ossindex.password=<your ossindex password here> \
  -M:clj-watson scan -p deps.edn
```
</details>

Example -T tool usage:
```shell
clojure -J-Dnvd.api.key=<your nvd nist api key here> \
  -Tclj-watson scan :p deps.edn
```

<details>
  <summary>With OSS Index enabled:</summary>

```shell
clojure -J-Dnvd.api.key=<your nvd nist api key here> \
        -J-Danalyzer.ossindex.user=<your ossindex user here> \
        -J-Danalyzer.ossindex.password=<your ossindex password here> \
  -Tclj-watson scan :p deps.edn
```
</details>

> [!CAUTION]
> You can specify system properties under `:jvm-opts` in your `deps.edn` under your `:clj-watson` alias, but be careful not to commit secrets to version control.

#### Using Environment Variables

Example -M usage:
```shell
CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here> \
  clojure -M:clj-watson scan -p deps.edn
```

<details>
  <summary>With OSS Index enabled:</summary>

```shell
CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here> \
  CLJ_WATSON_ANALYZER_OSSINDEX_USER=<your ossindex user here> \
  CLJ_WATSON_ANALYZER_OSSINDEX_PASSWORD=<your ossindex password here> \
  clojure -M:clj-watson scan -p deps.edn
```
</details>

Example -T tool usage:
```shell
CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here> \
  clojure -Tclj-watson scan :p deps.edn
```

<details>
  <summary>With OSS Index enabled:</summary>

```shell
CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here> \
  CLJ_WATSON_ANALYZER_OSSINDEX_USER=<your ossindex user here> \
  CLJ_WATSON_ANALYZER_OSSINDEX_PASSWORD=<your ossindex password here> \
  clojure -Tclj-watson scan :p deps.edn
```
</details>

> [!NOTE]
> In Bash, you can also export your environment variables prior to running your command, for example:
> ```shell
> export CLJ_WATSON_NVD_API_KEY=<your nvd nist api key here>
> export CLJ_WATSON_ANALYZER_OSSINDEX_USER=<your ossindex user here>
> export CLJ_WATSON_ANALYZER_OSSINDEX_PASSWORD=<your ossindex password here>
> clojure -M:clj-watson scan -p deps.edn
> ```

#### Using the `clj-watson.properties` File

Specify your options in your `clj-watson.properties` file:

```
# clj-watson.properties file
nvd.api.key=<your nvd nist api key here>
```

Or, with OSS Index enabled:
```
# clj-watson.properties file
nvd.api.key=<your nvd nist api key here>
analyzer.ossindex.user=<your ossindex user here>
analyzer.ossindex.password=<your ossindex password here>
```

`clj-watson` will pick up the `clj-watson.properties` file automatically if it is on the classpath, or you can specify it on the command line via the `-w` / `--clj-watson-properties` option:

Example -M usage:

```shell
clojure -M:clj-watson scan -p deps.edn --clj-watson-properties ./clj-watson.properties
```

Example -T usage:

```shell
clojure -Tclj-watson scan :p deps.edn :clj-watson-properties ./clj-watson.properties
```

> [!CAUTION]
> Be careful not to commit any secrets to version control.

## GitHub Advisory Database [experimental]

This approach doesn't need to download a database since it uses the
[GitHub Advisory Database](https://github.com/advisories) via its
[GraphQL API](https://docs.github.com/en/graphql/reference/objects#securityvulnerability),
and matches are made via package names.

In order to use this approach, it is necessary to generate a
[GitHub Personal Access Token (PAT)](https://docs.github.com/en/graphql/guides/forming-calls-with-graphql#authenticating-with-graphql)
to access the GraphQL API, or if you use GitHub Actions it is possible to use
their GitHub token.

An important thing to be aware of is that the API has a limit of 5,000
requests per hour/per PAT.

If you create a PAT or use the GitHub Action token, you can set it as an
environment variable named `GITHUB_TOKEN` and `clj-watson` will be able to use it.

#### Allow Listing Known CVE's

Sometimes, the transitive dependency tree is not under your control and it is
not always possible to override vulnerable dependencies.
You can allow a CVE for a limited period by adding a `clj-watson-config.edn`
configuration file to your classpath with the following structure:

```clojure
{:allow-list {:cves [{:cve-label "CVE-0000"
                      :expires "2000-01-01"}
                     {:cve-label "CVE-00000"
                      :expires "2000-01-01"}]}}
```

> Note: this is for the GitHub Advisory Database strategy only.

## Remediation suggestions

**The big difference between `clj-watson` and other tools!**

Since fixing the vulnerabilities found manually can be a truly frustrating
process, `clj-watson` provides a way to suggest a remediation.

It performs lookups for the whole dependency tree, checking if the latest
version of a parent dependency uses the secure version of the child dependency
until it reaches the direct dependency.

Given the following dependency tree,

```clojure
[dependency-a "v1"]
  [dependency-b "v1"]
    [dependency-c "v1"]
```

where `dependency-c` is vulnerable and fixing it would require a bump from `v1`
to `v2`, `clj-watson` will try to find a version of `dependency-a` that uses
a version of `dependency-b` that uses `dependency-c` at version `v2`, and then
`clj-watson` will propose updating `dependency-a`.

```clojure
{dependency-a {:mvn/version "v4"}}
```

If `clj-watson` does not find a version of `dependency-b` or `dependency-a` that
satisfies this condition, it will propose an exclusion instead:

```clojure
{dependency-a {:exclusions [dependency-b]}
 dependency-b {:mvn/version "v3"}}
```

In order to get the automated remediation suggestions, specify
the `--suggest-fix` or `-s` option when running `clj-watson`.

## Installation Notes

> [!IMPORTANT]
> You'll need to [setup your NVD API key](#nist-nvd-api).

See [Quick Start](#quick-start) for an overview.

### Tools Usage

`clj-watson` can be installed as a Clojure CLI tool, as shown under [Quick Start](#quick-start).  While
this is the easiest way to install the latest version and keep it up-to-date
(using `clojure -Ttools install-latest`), it also means using the key/value
EDN-style options for the CLI tool, which can, at first, seem a bit unwieldy.
Example equivalent usages:

```bash
clojure -Tclj-watson scan '{:fail-on-result true :deps-edn-path "deps.edn" :suggest-fix true :aliases ["*"]}'
# or:
clojure -Tclj-watson scan :fail-on-result true :deps-edn-path deps.edn :suggest-fix true :aliases '[*]'
```

If you aren't familiar with -T tools, you might be surprised that exceptions are thrown for what seem like non-exceptional things. 
For example a typo on the command line will show what you'd expect (explanation of error and usage help) but also show output that looks like this:

```
Execution error (ExceptionInfo) at clj-watson.entrypoint/scan$fn (entrypoint.clj:75).
usage error

Full report at:
/tmp/clojure-16796860161725335561.edn
```

This is the nature of -T tools, which are designed to be potentially chained, and therefore throw instead of exiting, and therefore exhibit this behaviour.

### Invoking with -Sdeps

An alternative invocation is via `-Sdeps`:

```bash
clojure -Sdeps '{:deps {io.github.clj-holmes/clj-watson {:git/tag "v6.0.1" :git/sha "b520351"}}}' \
  -M -m clj-watson.cli scan -p deps.edn
```

## CLI Options

You can get a full list of the available options by running:

```bash
clojure -M:clj-watson scan --help
```

This produces:

```
clj-watson

ARG USAGE:
 scan [options..]

OPTIONS:
  -p, --deps-edn-path <file>                                 Path of deps.edn file to scan [*required*]
  -o, --output <json|edn|stdout|stdout-simple|sarif>         Output type for vulnerability findings [stdout]
  -a, --aliases                                              Include deps.edn aliases in analysis, specify '*' for all.
                                                             For multiple, repeat arg, ex: -a alias1 -a alias2
  -t, --database-strategy <dependency-check|github-advisory> Vulnerability database strategy [dependency-check]
  -s, --suggest-fix                                          Include dependency remediation suggestions in vulnurability findings [false]
  -f, --fail-on-result                                       When enabled, exit with non-zero on any vulnerability findings
                                                             Useful for CI/CD [false]
  -c, --cvss-fail-threshold <score>                          Exit with non-zero when any vulnerability's CVSS base score is >= threshold
                                                             CVSS scores range from 0.0 (least severe) to 10.0 (most severe)
                                                             We interpret a score of 0.0 as suspicious
                                                             Missing or suspicious CVSS base scores are conservatively derived
                                                             Useful for CI/CD
  -h, --help                                                 Show usage help

OPTIONS valid when database-strategy is dependency-check:
  -w, --clj-watson-properties <file>                         Path of an additional, optional properties file
                                                             Overrides values in dependency-check.properties
                                                             If not specified classpath is searched for clj-watson.properties
      --run-without-nvd-api-key                              Run without an nvd.api.key configured.
                                                             It will be slow and we cannot recommend it.
                                                             See docs for configuration. [false]
```

> [!TIP]
> If you are running `clj-watson` as a tool run:
> ```bash
> clojure -Tclj-watson scan :help true
> ```

## Execution

The minimum needed to run `clj-watson` is to provide the path to a `deps.edn`
file, but it is recommended that you also provide the `-s` option so
`clj-watson` will try to suggest remediations for any vulnerabilities found.

> [!IMPORTANT]
> You must first [setup your NVD API key](#nist-nvd-api).

```bash
clojure -M:clj-watson scan -p deps.edn -s
```
```
...

Dependency Information
-----------------------------------------------------
NAME: dependency-e
VERSION: 1

DEPENDENCY FOUND IN:

[dependency-a]
        [dependency-b]

[dependency-a]
        [dependency-c]
                [dependency-d]

FIX SUGGESTION: {dependency-a {:mvn/version "3"}}

Vulnerabilities
-----------------------------------------------------

SEVERITY: Information not available.
IDENTIFIERS: CVE-2022-1000000
CVSS: 7.5 (version 3.1)
PATCHED VERSION: 1.55

SEVERITY: Information not available.
IDENTIFIERS: CVE-2022-2000000
CVSS: 5.3
PATCHED VERSION: 1.55
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```

## CVSS Scores & Severities

A Common Vulnerability Scoring System (CVSS) score is a number from `0.0` to `10.0` that conveys the severity of a vulnerability.
There are multiple different scores available, but `clj-watson` will always only report and use the base score.

Over the years, CVSS has been revised a number of times.
As of this writing, you can expect to see versions `2.0`, `3.0`, `3.1`, and `4.0`.
Sometimes, a single vulnerability will specify scores from multiple CVSS versions.
To err on the side of caution, `clj-watson` will always use and report the highest base score.

If you are curious about other scores, you can always bring up the CVE on the NVD NIST website, for an arbitrary example: https://nvd.nist.gov/vuln/detail/CVE-2022-21724.

A severity is `low`, `medium`, `high`, or `critical`, and is based on the CVSS score.
See the [NVD NIST website description for details](https://nvd.nist.gov/vuln-metrics/cvss).

> [!TIP]
> The experimental `github-advisory` strategy has some differences:
> - In addition to `medium` can return a severity of `moderate` which is equivalent to `medium`.
`clj-watson` will always convert `moderate` to `medium` for `github-advisory`.
> - It only populates scores from a single CVSS version.
> - It does not always populate the CVSS score, or populates it with `0.0`.

## Failing on Findings

By default, `clj-watson` exits with `0`.

You can opt to have `clj-watson` exit with a non-zero value when it detects vulnerabilities, which can be useful when running from a continuous integration (CI) server or service.

Specify `--fail-on-result` (or `-f`) to exit with non-zero when any vulnerabilities are detected.

Example usages:

```
clojure -M:clj-watson scan --deps-edn-path deps.edn --fail-on-result
clojure -Tclj-watson scan :deps-edn-path deps.edn :fail-on-result true
```

For finer control use `--cvss-fail-threshold` (or `-c`) to specify a CVSS score at which to fail.
When any detected vulnerability has a score equal to or above the threshold, `clj-watson` will summarize vulnerabilities that have met the threshold and exit with non-zero.

Example usages:
```
clojure -M:clj-watson scan --deps-edn-path deps.edn --cvss-fail-threshold 5.8
clojure -Tclj-watson scan :deps-edn-path deps.edn :cvss-fail-threshold 5.8
```

Example summary:

```
CVSS fail score threshold of 5.8 met for:

  Dependency                                     Version Identifiers      CVSS Score
  org.apache.httpcomponents/httpclient           4.1.2   CVE-2014-3577    5.8 (version 2.0)
  com.fasterxml.jackson.core/jackson-annotations 2.4.0   CVE-2018-1000873 6.5 (version 3.1)
  com.fasterxml.jackson.core/jackson-core        2.4.2   CVE-2018-1000873 6.5 (version 3.1)
  org.jsoup/jsoup                                1.6.1   CVE-2021-37714   7.5 (version 3.1)
  com.fasterxml.jackson.core/jackson-databind    2.4.2   CVE-2020-9548    9.8 (version 3.1)
  org.clojure/clojure                            1.8.0   CVE-2017-20189   9.8 (version 3.1)
  org.codehaus.plexus/plexus-utils               3.0     CVE-2017-1000487 9.8 (version 3.1)
```

When the score is missing or suspicious-looking, `clj-watson` will conservatively derive a score and indicate how it has done so (see `httpclient` below):

```
CVSS fail score threshold of 5.8 met for:

  Dependency                                  Version Identifiers                          CVSS Score
  org.jsoup/jsoup                             1.6.1   GHSA-m72m-mhq2-9p6c CVE-2021-37714   7.5 (version 3.1)
  com.fasterxml.jackson.core/jackson-databind 2.4.2   GHSA-qxxx-2pp7-5hmx CVE-2017-7525    9.8 (version 3.1)
  com.mchange/c3p0                            0.9.5.2 GHSA-q485-j897-qc27 CVE-2018-20433   9.8 (version 3.0)
  org.clojure/clojure                         1.8.0   GHSA-jgxc-8mwq-9xqw CVE-2017-20189   9.8 (version 3.1)
  org.codehaus.plexus/plexus-utils            3.0     GHSA-8vhq-qq4p-grq3 CVE-2017-1000487 9.8 (version 3.1)
  org.apache.httpcomponents/httpclient        4.1.2   GHSA-2x83-r56g-cv47 CVE-2012-6153    10.0 (score 0.0 suspicious - derived from High severity)
```

## Output & Logging

`clj-watson` uses [SLFJ4](https://www.slf4j.org/) and [Logback](https://logback.qos.ch) to collect and filter meaningful log output from its dependencies.
This output goes to `stderr`.

It writes settings and vulnerability findings to `stdout`.

## Who uses it

- [180 Seguros](https://180s.com.br)
- [org.clojure/tools.deps](https://github.com/clojure/tools.deps)
- [World Singles Networks](https://worldsinglesnetworks.com/)
- [clj-yaml](https://github.com/clj-commons/clj-yaml)
- [pomegranate](https://github.com/clj-commons/pomegranate)

Are you using clj-watson? Let us know and we'll add your project here!

## Development

### nREPL

```bash
clojure -M:nREPL -m nrepl.cmdline
```

### Test

```bash
clojure -M:test
```

### Lint

We use [clojure-lsp from the command line](https://clojure-lsp.io/api/cli/) to lint:
```bash
clojure -M:clojure-lsp format
clojure -M:clojure-lsp clean-ns
clojure -M:clojure-lsp diagnostics
```

### Security

We use [clj-holmes](https://github.com/clj-holmes/clj-holmes) to check for potentially vulnerable patterns in clj-watson source code:
```bash
clj-holmes scan -p .
```

## License and Copyright

Copyright © 2021-2024 Matheus Bernardes

Distributed under the Eclipse Public License version 2.0.
