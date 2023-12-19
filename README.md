# clj-watson
Clojure's software composition analysis (SCA).
clj-watson scans dependencies in a clojure `deps.edn` seeking for vulnerable direct/transitive dependencies and build a report with all the information needed to help you understand how the vulnerability manifest in your software.

## Quick Start

clj-watson can be added as an alias on a per-project basis in the project `deps.edn` file:

```clojure
;; in :aliases
  :clj-watson {:replace-deps {io.github.clj-holmes/clj-watson {:git/tag "v4.1.3" :git/sha "56dfd3e"}}
               :main-opts ["-m" "clj-watson.cli" "scan"]}
```

Then run it with:

```bash
clojure -M:clj-watson -p deps.edn
```

The first time it runs, it will download the vulnerability database, which can take a few minutes. Subsequent runs will be much faster.

It can also be installed as a Clojure CLI tool:

```bash
clojure -Ttools install-latest :lib io.github.clj-holmes/clj-watson :as clj-watson
```

Then run it with:

```bash
clojure -Tclj-watson scan :deps-edn-path '"deps.edn"' :output '"stdout"'
#or:
clojure -Tclj-watson scan '{:deps-edn-path "deps.edn" :output "stdout"}'
```

(this is somewhat verbose now but it will be improved over the next few releases)

# How it works
## Vulnerability database strategies
clj-watson supports two methods for vulnerabilities scan.

### dependency-check
[dependency-check](https://github.com/jeremylong/DependencyCheck) is the most used method around the clojure/java sca tools, it downloads all vulnerabilities from nvd and stores it in a database (located in `/tmp/db`), compose a [cpe](https://nvd.nist.gov/products/cpe) based on the dependencies, scans all jars in the classpath and matches vulnerabilities using it.

#### NIST NVD API

As of version v5.0.0, `clj-watson` uses
[`DependencyCheck` 9.0.x](https://github.com/jeremylong/DependencyCheck/tree/main?tab=readme-ov-file#900-upgrade-notice)
which switches from the earlier NVD data feeds to the new NIST NVD API.

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

### Github advisory database [experimental]
It doesn't need to download a database since it uses the [github advisory database](https://github.com/advisories) via the [graphql api](https://docs.github.com/en/graphql/reference/objects#securityvulnerability), matches are made via package name.
But there's a requirements to use it, it's necessary to generate a [Github PAT (Personal Access Token)](https://docs.github.com/en/graphql/guides/forming-calls-with-graphql#authenticating-with-graphql) to access graphql api or if you use Github actions it's possible to use their Github token.
Another important thing is that the api has a limit of 5K requests per hour/per PAT.
If you create a PAT or uses the github action token just set it in as an environment variabe named `GITHUB_TOKEN` to clj-watson be able to use it.

#### Allow Listing Known CVE's

Sometimes the dependency tree is not under your control and overrides are not possible,
but you can allways allow a CVE for a limited period by adding a config file at `resources/clj-watson-config.edn`:

```clojure
{:allow-list {:cves [{:cve-label "CVE-0000"
                      :expires "2000-01-01"}
                     {:cve-label "CVE-00000"
                      :expires "2000-01-01"}]}}
```

## Remediation suggestion
#### The big difference from clj-watson to other tools.
Since fixing the found vulnerabilities manually could be truly frustrating `clj-watson` provides a way to suggest a remediation.
It basically lookups the whole dependency tree finding if the latest version of a parent dependency uses the secure version of the child dependency until it reaches the direct dependency.
Given the following dependency tree,
```
[dependency-a "v1"]
  [dependency-b "v1"]
    [dependency-c "v1"]
```
where the `dependency-c` is vulnerable and to fix it is necessary to bump from `v1` to `v2` clj-watson will try to find a version of `dependency-a` that uses `dependency-b` in a version that uses `dependency-c` on version `v2` and then propose a bump to `dependency-a`.
```clojure
{dependency-a {:mvn/version "v4"}}
```
If clj-watson does not find a version of dependency-b or dependency-a that satisfies this statement it'll propose an exclusion.
```clojure
{dependency-a {:exclusions [dependency-b]}
 dependency-b {:mvn/version "v3"}}
````
In order to get the auto remediate suggestion it's necessary to provide a `--suggest-fix|-s` on the clj-watson execution.
# Installation
It's possible to install clj-watson as a clojure tool and invoke it.
```bash
$ clojure -Ttools install io.github.clj-holmes/clj-watson '{:git/tag "v4.1.2" :git/sha "eb15492"}' :as clj-watson
$ clojure -Tclj-watson scan '{:output "stdout" :fail-on-result true :deps-edn-path "deps.edn" :suggest-fix true :aliases ["*"] :database-strategy "dependency-check"}'
```
It can also be called directly.
```bash
$ clojure -Sdeps '{:deps {io.github.clj-holmes/clj-watson {:git/tag "v4.1.3" :git/sha "56dfd3e"}}}' -M -m clj-watson.cli scan -p deps.edn
```
Or you can just add it to your project `deps.edn`
```clojure
{:deps {}
 :aliases
 {:clj-watson {:extra-deps {io.github.clj-holmes/clj-watson {:git/tag "v4.1.3" :git/sha "56dfd3e"}}
               :main-opts ["-m" "clj-watson.cli" "scan"]}}}
```

# CLI Options
```bash
$ clojure -M:clj-watson scan -\?
NAME:
 clj-watson scan - Performs a scan on a deps.edn file

USAGE:
 clj-watson scan [command options] [arguments...]

OPTIONS:
   -p, --deps-edn-path S*                                                      path of deps.edn to scan.
   -o, --output edn|json|stdout|stdout-simple|sarif                report      Output type.
   -a, --aliases S                                                             Specify a alias that will have the dependencies analysed alongside with the project deps.It's possible to provide multiple aliases. If a * is provided all the aliases are going to be analysed.
   -d, --dependency-check-properties S                                         [ONLY APPLIED IF USING DEPENDENCY-CHECK STRATEGY] Path of a dependency-check properties file. If not provided uses resources/dependency-check.properties.
   -w, --clj-watson-properties S                                               [ONLY APPLIED IF USING DEPENDENCY-CHECK STRATEGY] Path of an additional, optional properties file.
   -t, --database-strategy dependency-check|github-advisory  dependency-check  Vulnerability database strategy.
   -s, --[no-]suggest-fix                                                      Suggest a new deps.edn file fixing all vulnerabilities found.
   -f, --[no-]fail-on-result                                                   Enable or disable fail if results were found (useful for CI/CD).
   -?, --help
```

By default, when using the DEPENDENCY-CHECK strategy, clj-watson will load
its own `dependency-check.properties` file, and then look for a
`clj-watson.properties` file on the classpath and load that if found, for
additional properties to apply to the dependency-check scan.

If you provide `-d` (or `--dependency-check-properties`) then clj-watson will
load that file instead of its own `dependency-check.properties` file so it
needs to be a complete properties file, not just the properties you want to
override.

If you provide `-w` (or `--clj-watson-properties`) then clj-watson will load
that file and apply those properties to the dependency-check scan. This is
in addition to the properties loaded from the `dependency-check.properties`
or the `-d` file. This can be useful to override just a few properties.

# Execution
The minimum necessary to execute clj-watson is to provide the path to a `deps.edn` file, but it's recommended that you all provide the `-s` option so `clj-watson` will try to provide a remediation suggestion to the vulnerabilities.

```bash
$ clojure -M:clj-watson -p deps.edn
Downloading/Updating database.
Download/Update completed.
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
CVE: CVE-2022-1000000
CVSSV3: 7.5
CVSSV2: 5.0
SUGGESTED BUMP: 1.55

CVE: CVE-2022-2000000
CVSSV3: 5.3
CVSSV2: 5.0
SUGGESTED BUMP: 1.55
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```
# Who uses it
- [180 Seguros](https://180s.com.br)
- [World Singles Networks](https://worldsinglesnetworks.com/)

# Development
## nREPL
```
clj -M:nREPL -m nrepl.cmdline
```
## Lint
```
clj -M:clojure-lsp format
clj -M:clojure-lsp clean-ns
```
