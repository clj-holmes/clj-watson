# `clj-watson`

A Clojure tool for vulnerability checking.

`clj-watson` is a software composition analysis (SCA) tool that scans
dependencies specified in a Clojure `deps.edn` file looking for vulnerable direct and
transitive dependencies, and builds a report with all the information needed
to help you understand how the vulnerabilities manifest in your software.

`clj-watson` can suggest a remediation for the vulnerabilities found,
and can check against both the
[NIST National Vulnerability Database (NVD)](https://nvd.nist.gov/)
(by default) and the
[GitHub Advisory Database](https://github.com/advisories)
(experimental).

## Quick Start

`clj-watson` can be added as an alias either on a per-project basis in your
project's `deps.edn` file or in your user `deps.edn` file
(either `~/.clojure/deps.edn` or `~/.config/clojure/deps.edn`):

```clojure
  ;; in :aliases
  :clj-watson {:replace-deps
               {io.github.clj-holmes/clj-watson
                {:git/tag "v5.1.3" :git/sha "5812615"}}
               :main-opts ["-m" "clj-watson.cli"]}
```

> [!IMPORTANT]
> You'll need to first [setup your NVD API key](#nist-nvd-api).

Then you can run it with:

```bash
clojure -M:clj-watson scan -p deps.edn
```

The first time it runs, it will download the entire vulnerability database, which
can take several minutes. Subsequent runs will be much faster.

> [!NOTE] 
> The database is stored in the `/tmp/db/` folder (on macOS/Linux) - in case you ever need to delete that folder, if it looks like the database is corrupted.

`clj-watson` can also be installed as a Clojure CLI tool:

```bash
clojure -Ttools install-latest :lib io.github.clj-holmes/clj-watson :as clj-watson
```

Then can be run via:

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

> [!NOTE]
> `:aliases` (or `:a`) should be specified as a vector of keywords (or symbols), e.g., `:a '[:foo :bar]`, whereas it would be specified multiple times (as strings) in the regular CLI, `-a foo -a bar`.

# How it works

## Vulnerability database strategies

`clj-watson` supports two methods for vulnerabilities scans.

### DependencyCheck

[DependencyCheck](https://github.com/jeremylong/DependencyCheck) is the most
widely used method among the Clojure/Java SCA tools. It:
1. Downloads a database of known vulnerabilities from [NIST NVD](https://nvd.nist.gov/), storing it locally under your `/tmp/db/` folder
3. Scans JARs from dependencies specified in your `deps.edn`
4. Composes a [Common Platform Enumeration (CPE)](https://nvd.nist.gov/products/cpe) based on your dependencies
5. Returns any matching vulnerabilities

`clj-watson` then reports these findings to you, optionally with [potential remediations](#remediation-suggestions).

> [!IMPORTANT]
> We _always_ recommend using the latest version of `clj-watson`, but as a minimum upgrade to v5.1.3.
> All earlier versions of `clj-watson` are officially deprecated.
> Older versions of `clj-watson` use older problematic versions of DependencyCheck, which NIST is now blocking.

#### NIST NVD API

> [!IMPORTANT]
> The [NIST NVD data feeds discourage access without API keys by heavily throttling anonymous requests](https://nvd.nist.gov/general/news/API-Key-Announcement).
> So, request one and use it.

It is easy to [request an API key](https://github.com/jeremylong/DependencyCheck/tree/main?tab=readme-ov-file#nvd-api-key-highly-recommended).

You can specify you key via:

1. The `nvd.api.key` Java system property on the command line
2. Or, an `nvd.api.key` entry in your `clj-watson.properties` file

> [!CAUTION]
> Keeping your nvd api key secret is your responsibility.
> This is not a hugely sensitive secret, but you don't want others to use your key.
> You do not want to check it into any public version control system.

##### Via Java System Property on the Command Line

Example usage:

```shell
clojure -J-Dnvd.api.key=<your key here> -M:clj-watson scan -p deps.edn
```

Or:

```shell
clojure -J-Dnvd.api.key=<your key here> -Tclj-watson scan :p deps.edn
```

Replace `<your key here>` with your actual api key.

> [!CAUTION]
> You could specify this system property under `:jvm-opts` in your `deps.edn` under your `:clj-watson` alias, but be careful not to commit it to version control. 

##### Via the `clj-watson.properties` File

Specify your key in your `clj-watson.properties` file:

```
# clj-watson.properties file
nvd.api.key=<your key here>
```
Replace `<your key here>` with your actual api key.

`clj-watson` will pick up `clj-watson.properties` automatically if it is on the classpath, or you can specify it on the command line via the `-w` / `--clj-watson-properties` option:


```shell
clojure -M:clj-watson scan -p deps.edn --clj-watson-properties ./clj-watson.properties
```

Or:

```shell
clojure -Tclj-watson scan :p deps.edn :clj-watson-properties ./clj-watson.properties
```

> [!CAUTION] 
> Be careful not to commit your key to version control.

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

> [!IMPORTANT]
> You'll need to [setup your NVD API key](#nist-nvd-api). 

`clj-watson` can be installed as a Clojure CLI tool, as shown above. While
this is the easiest way to install the latest version and keep it up-to-date
(using `clojure -Ttools install-latest`), it also means using the key/value
EDN-style options for the CLI tool, which can at first seem a bit unwieldy:

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
 {:clj-watson {:replace-deps {io.github.clj-holmes/clj-watson {:git/tag "v5.1.3" :git/sha "5812615"}}
               :main-opts ["-m" "clj-watson.cli"]}}}
```

and invoke it with:

```bash
clojure -M:clj-watson scan -p deps.edn
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
      --run-without-nvd-api-key                              Run without an nvd.api.key configured.
                                                             It will be slow and we cannot recommend it.
                                                             See docs for configuration. [false]
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
`clj-watson` will try to suggest remediations for any vulnerabilities found.

> [!IMPORTANT]
> You'll need to first [setup your NVD API key](#nist-nvd-api). 

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

# Output & Logging

`clj-watson` uses [SLFJ4](https://www.slf4j.org/) and [Logback](https://logback.qos.ch) to collect and filter meaningful log output from its dependencies.
This output goes to `stderr`.

It writes settings and vulnerability findings to `stdout`.

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
