# clj-watson
Clojure's software composition analysis (SCA).

# Install
It's possible to install clj-watson as clojure tool and invoke it.
```bash
$ clojure -Ttools install io.github.clj-holmes/clj-watson '{:git/tag "v2.1.0" :git/sha "468f6fe"}'
$ clojure -Tclj-watson clj-watson.entrypoint/-main '{:output "stdout" :dependency-check-properties nil :fail-on-result true :deps-edn-path "deps.edn" :suggest-fix true :aliases ["*"]}'
```
It can be called directly.
```bash
$ clojure -Sdeps '{:deps {io.github.clj-holmes/clj-watson {:git/tag "v2.1.0" :git/sha "468f6fe"}}}' -M -m clj-watson.cli scan -p deps.edn
```
Or you can just add it to your project `deps.edn`
```clojure
{:deps {}
 :aliases
 {:clj-watson {:deps {io.github.clj-holmes/clj-watson {:git/tag "v2.1.0" :git/sha "468f6fe"}}
               :main-opts ["-m" "clj-watson.cli" "scan"]}}}
```

# Usage
```bash
$ clojure -M:clj-watson scan -\? 
NAME:
 clj-watson scan - Performs a scan on a deps.edn file

USAGE:
 clj-watson scan [command options] [arguments...]

OPTIONS:
   -p, --deps-edn-path S*                       path of deps.edn to scan.
   -d, --dependency-check-properties S          path of a dependency-check properties file. If not provided uses resources/dependency-check.properties.
   -o, --output edn|json|stdout         stdout  Output type.
   -a, --aliases S                              Specify a alias that will have the dependencies analysed alongside with the project deps.It's possible to provide multiple aliases. If a * is provided all the aliases are going to be analysed.
   -s, --[no-]suggest-fix                       Suggest a new deps.edn file fixing all vulnerabilities found.
   -f, --[no-]fail-on-result                    Enable or disable fail if results were found (useful for CI/CD).
   -?, --help
```

# Execution
clj-watson scans a clojure deps project using [dependency-check](https://github.com/jeremylong/DependencyCheck) seeking for vulnerable direct/transitive dependencies and add all the dependency tree information to help understading how the vulnerability manifest.

```bash
$ clojure -M:clj-watson scan scan -p deps.edn -s
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

# Auto remediation
Since fixing the found vulnerabilities manually could be truly frustrating clj-watson provides a way to find all possible solutions. It basically lookup the role dependency tree finding if the latest version of dependency contains the safe version of the child dependency until reachs the direct dependency. e.g:
Given the following dependnecy tree,

```
[dependency-a "v1"]
  [dependency-b "v1"]
    [dependency-c "v1"]
```

where the `dependency-c` is vulnerable and to fix it's necessary to bump it to `v2` clj-watson will try to find a version of `dependency-a` that uses `dependency-b` in a version that uses `dependency-c` on version `v2` and then propose a bump to `dependency-a`.

```clojure
{dependency-a {:mvn/version "v4"}
```

If clj-watson does not find a version of dependency-b or dependency-a that satifies this statement it'll propose an exclusion.

```clojure
{dependency-a {:exclusions [dependency-b]}
 dependency-b {:mvn/version "v3"}}
````

In order to get the auto remediate suggestion it's just necessary to provide a `--suggest-fix` on the clj-watson execution.

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
clj -M:lint
clj -M:lint-fix
```
