# clj-watson
Clojure's software composition analysis (SCA).
clj-watson scans dependencies in a clojure `deps.edn` seeking for vulnerable direct/transitive dependencies and build a report with all the information needed to help you understand how the vulnerability manifest in your software.

# How it works
## Vulnerability database strategies
clj-watson supports two methods for vulnerabilities scan.

### dependency-check
[dependency-check](https://github.com/jeremylong/DependencyCheck) is the most used method around the clojure/java sca tools, it downloads all vulnerabilities from nvd and stores it in a database (located in `/tmp/db`), compose a [cpe](https://nvd.nist.gov/products/cpe) based on the dependencies, scans all jars in the classpath and matches vulnerabilities using it.

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
$ clojure -Ttools install io.github.clj-holmes/clj-watson '{:git/tag "v4.1.1" :git/sha "efa3420"}' :as clj-watson
$ clojure -Tclj-watson scan '{:output "stdout" :dependency-check-properties nil :fail-on-result true :deps-edn-path "deps.edn" :suggest-fix true :aliases ["*"] :database-strategy "dependency-check"}'
```
It can also be called directly.
```bash
$ clojure -Sdeps '{:deps {io.github.clj-holmes/clj-watson {:git/tag "v4.1.1" :git/sha "efa3420"}}}' -M -m clj-watson.cli scan -p deps.edn
```
Or you can just add it to your project `deps.edn`
```clojure
{:deps {}
 :aliases
 {:clj-watson {:extra-deps {io.github.clj-holmes/clj-watson {:git/tag "v4.1.1" :git/sha "efa3420"}}
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
   -t, --database-strategy dependency-check|github-advisory  dependency-check  Vulnerability database strategy.
   -s, --[no-]suggest-fix                                                      Suggest a new deps.edn file fixing all vulnerabilities found.
   -f, --[no-]fail-on-result                                                   Enable or disable fail if results were found (useful for CI/CD).
   -?, --help
```

# Execution
The minimum necessary to execute clj-watson is to provide the path to a `deps.edn` file, but it's recommended that you all provide the `-s` option so `clj-watson` will try to provide a remediation suggestion to the vulnerabilities.

```bash
$ clojure -M:clj-watson scan scan -p deps.edn
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
## Build
```
clj -X:depstar
```
## Lint
```
clj -M:clojure-lsp format
clj -M:clojure-lsp clean-ns
```
