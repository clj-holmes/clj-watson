# clj-watson

A Clojure tool for vulnerability checking.

`clj-watson` is a software composition analysis (SCA) tool, that scans
dependencies in a Clojure `deps.edn` file looking for vulnerable direct and
transitive dependencies, and builds a report with all the information needed
to help you understand how the vulnerabilities manifest in your software.

`clj-watson` can suggest a remediation for the vulnerabilities found,
and can check against both the
[NIST National Vulnerability Database (NVD)](https://nvd.nist.gov/)
(by default) and the
[GitHub Advisory Database](https://github.com/advisories)
(experimental).

## Quick Start

`clj-watson` can be added as an alias either on a per-project basis in the
project's `deps.edn` file or in your user `deps.edn` file
(either `~/.clojure/deps.edn` or `~/.config/clojure/deps.edn`):

```clojure
  ;; in :aliases
  :clj-watson {:replace-deps
               {io.github.clj-holmes/clj-watson
                {:git/tag "v5.1.3" :git/sha "5812615"}}
               :main-opts ["-m" "clj-watson.cli" "scan"]}
```

Then you can run it with:

```bash
clojure -M:clj-watson -p deps.edn
```

The first time it runs, it will download the vulnerability database, which
can take a few minutes. Subsequent runs will be much faster.

> Note: the database is stored in the `/tmp/db/` folder (on macOS/Linux) - in case you ever need to delete that folder, if it looks like the database is corrupted.

It can also be installed as a Clojure CLI tool:

```bash
clojure -Ttools install-latest :lib io.github.clj-holmes/clj-watson :as clj-watson
```

Then run it with:

```bash
clojure -Tclj-watson scan :deps-edn-path '"deps.edn"' :output '"stdout"'
# or:
clojure -Tclj-watson scan '{:deps-edn-path "deps.edn" :output "stdout"}'
```

The tool option keywords match the long-form CLI option names (see below)
but the abbreviations are also supported. In addition, any string option may
be specified as a bare Clojure symbol (if it is legally representable as such),
which means the above command-line can be simplified to just:

```bash
clojure -Tclj-watson scan :p deps.edn
```

`:output` can be omitted because it defaults to `stdout`, and `:deps-edn-path`
can be shortened to `:p` (matching the `-p` short form of `--deps-edn-path`).

> Note: `:aliases` (or `:a`) should be specified as a vector of keywords (or symbols), e.g., `:a '[:foo :bar]`, whereas it would be specified multiple times (as strings) in the regular CLI, `-a foo -a bar`.

# How it works

## Vulnerability database strategies

`clj-watson` supports two methods for vulnerabilities scan.

### DependencyCheck

[DependencyCheck](https://github.com/jeremylong/DependencyCheck) is the most
widely used method among the Clojure/Java SCA tools. It downloads all
vulnerabilities from NVD and stores it in a database (located in the `/tmp/db/`
folder), composes a
[Common Platform Enumeration (CPE)](https://nvd.nist.gov/products/cpe) based on
the dependencies, scans all JARs in the classpath and matches vulnerabilities
using it.

* `clj-watson` v5.1.3 onward uses DependencyCheck 10.0.x and the new NIST NVD API.
  * `clj-watson` v5.0.0..v5.1.2 used DependencyCheck 9.0.x which caused the NIST NVD API to be overwhelmed; please update to v5.1.3!
* `clj-watson` v4.x.x uses an earlier version of DependencyCheck and the old NVD data feeds, which have been deprecated.

#### NIST NVD API

As of version v5.0.0, `clj-watson` switched to
[`DependencyCheck` 9.0.x](https://github.com/jeremylong/DependencyCheck/tree/main?tab=readme-ov-file#900-upgrade-notice)
which switches from the earlier NVD data feeds to the new NIST NVD API.
**NIST are forcing everyone to upgrade to 10.0.3 or later so please use `clj-watson` v5.1.3 or later!**

This new API heavily throttles anonymous requests, so it is
[highly recommended to get an API key](https://github.com/jeremylong/DependencyCheck/tree/main?tab=readme-ov-file#nvd-api-key-highly-recommended)
in order to use the API efficiently.

Read the [NIST NVD announcement](https://nvd.nist.gov/general/news/API-Key-Announcement) for more information.

Once you have an API key, you can provide it to `clj-watson` via the `nvd.api.key`
property in the optional `clj-watson.properties` file, either on the classpath
you use to run `clj-watson` or via the `-w` / `--clj-watson-properties`
command-line option:

```
# clj-watson.properties file
nvd.api.key=...your key here...
```

### GitHub Advisory Database [experimental]

This approach doesn't need to download a database since it uses the
[GitHub Advisory Database](https://github.com/advisories) via its
[GraphQL API](https://docs.github.com/en/graphql/reference/objects#securityvulnerability),
and matches are made via package names.

In order to use this approach, it is necessary to generate a
[GitHub Personal Access Token (PAT)](https://docs.github.com/en/graphql/guides/forming-calls-with-graphql#authenticating-with-graphql)
to access the GraphQL API, or if you use GitHub Actions it is possible to use
their GitHub token.

Another important thing to be aware of is that the API has a limit of 5,000
requests per hour/per PAT.

If you create a PAT or use the GitHub Action token, you can set it as an
environment variable named `GITHUB_TOKEN` and `clj-watson` will be able to use it.

#### Allow Listing Known CVE's

Sometimes the transitive dependency tree is not under your control and it is
not always possible to override versions of dependencies that are vulnerable.
You can allow a CVE for a limited period by adding a `clj-watson-config.edn`
configuration file to your classpath with the following structure:

```clojure
{:allow-list {:cves [{:cve-label "CVE-0000"
                      :expires "2000-01-01"}
                     {:cve-label "CVE-00000"
                      :expires "2000-01-01"}]}}
```

> Note: this is for the GitHub Advisory Database strategy only.

## Remediation suggestion

**The big difference between `clj-watson` and other tools!**

Since fixing the vulnerabilities found manually can be a truly frustrating
process `clj-watson` provides a way to suggest a remediation.

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

In order to get the automated remediation suggestions, provide
the `--suggest-fix` or `-s` option when running `clj-watson`.

# Installation

`clj-watson` can be installed as a Clojure CLI tool, as shown above. While
this is the easiest way to install the latest version and keep it up-to-date
(using `clojure -Ttools install-latest`), it also means using the key/value
EDN-style options for the CLI tool which can be a bit unwieldy as present:

```bash
clojure -Tclj-watson scan '{:output "stdout" :fail-on-result true :deps-edn-path "deps.edn" :suggest-fix true :aliases ["*"] :database-strategy "dependency-check"}'
# or:
clojure -Tclj-watson scan :f true :p deps.edn :s true :a '[*]'
```

Both `:output` (`:o`) and `:database-strategy` (`:t`) can be omitted because
they default to `"stdout"` and `"dependency-check"` respectively.

In addition to the CLI tool install, shown above, it can also be invoked
directly via the Clojure CLI, by specifying `clj-watson` as a dependency
via `-Sdeps`:

```bash
clojure -Sdeps '{:deps {io.github.clj-holmes/clj-watson {:git/tag "v5.1.3" :git/sha "5812615"}}}' -M -m clj-watson.cli scan -p deps.edn
```
Or you can just add it to your `deps.edn` file as an alias:

```clojure
{:deps {}
 :aliases
 {:clj-watson {:extra-deps {io.github.clj-holmes/clj-watson {:git/tag "v5.1.3" :git/sha "5812615"}}
               :main-opts ["-m" "clj-watson.cli" "scan"]}}}
```

and invoke it with:

```bash
clojure -M:clj-watson -p deps.edn
```

# CLI Options

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
  -h, --help                                                 Show usage help

OPTIONS valid when database-strategy is dependency-check:
  -d, --dependency-check-properties <file>                   Path of a dependency-check properties file
                                                             If not provided uses resources/dependency-check.properties
  -w, --clj-watson-properties <file>                         Path of an additional, optional properties file
                                                             Overrides values in dependency-check.properties
                                                             If not specified classpath is searched for cljwatson.properties
```

By default, when using the DEPENDENCY-CHECK strategy, `clj-watson` will load
its own `dependency-check.properties` file, and then look for a
`clj-watson.properties` file on the classpath and load that if found, for
additional properties to apply to the DependencyCheck scan.

If you provide `-d` (or `--dependency-check-properties`) then `clj-watson` will
load that file instead of its own `dependency-check.properties` file so it
needs to be a complete properties file, not just the properties you want to
override.

If you provide `-w` (or `--clj-watson-properties`) then `clj-watson` will load
that file and apply those properties to the dependency-check scan. This is
in addition to the properties loaded from the `dependency-check.properties`
or the `-d` file. This can be useful to override just a few properties.

# Execution

The minimum needed to run `clj-watson` is to provide the path to a `deps.edn`
file, but it is recommended that you also provide the `-s` option so
`clj-watson` will try to suggest a remediation for any vulnerabilities found.

```bash
clojure -M:clj-watson -p deps.edn
```
```
...
Downloading/Updating database.
Download/Update completed.
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
CVSS: 7.5
PATCHED VERSION: 1.55

SEVERITY: Information not available.
IDENTIFIERS: CVE-2022-2000000
CVSS: 5.3
PATCHED VERSION: 1.55
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```

# Who uses it

- [180 Seguros](https://180s.com.br)
- [World Singles Networks](https://worldsinglesnetworks.com/)

# Development

## nREPL

```bash
clojure -M:nREPL -m nrepl.cmdline
```

## Test

```bash
clojure -M:test
```

## Lint

We use [clojure-lsp from the command line](https://clojure-lsp.io/api/cli/) to lint:
```bash
clojure -M:clojure-lsp format
clojure -M:clojure-lsp clean-ns
clojure -M:clojure-lsp diagnostics
```

## Security

We use [clj-holmes](https://github.com/clj-holmes/clj-holmes) to check for potentially vulnerable patterns in clj-watson source code:
```bash
clj-holmes scan -p .
```

# License and Copyright

Copyright © 2021-2024 Matheus Bernardes

Distributed under the Eclipse Public License version 2.0.
