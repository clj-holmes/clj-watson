# clj-watson
Clojure's software composition analysis (SCA).

# nREPL
`clj -M:nREPL -m nrepl.cmdline`

# Build
`clj -X:depstar`

# Lint
```
clj -M:lint
clj -M:lint-fix
```

# Options
```
NAME:
clj-watson scan - Performs a scan on a deps.edn file

USAGE:
clj-watson scan [command options] [arguments...]

OPTIONS:
-p, --deps-edn-path S*                       path of deps.edn to scan
-d, --dependency-check-properties S          path of deps.edn to scan
-o, --output edn|json|stdout         stdout  Output type
-s, --[no-]suggest-fix                       Suggest a new deps.edn file fixing all vulnerabilities found.
-f, --[no-]fail-on-result                    Enable or disable fail if results were found (useful for CI/CD).
-?, --help
```

# Executing
`java -jar target/clj-watson.jar scan -p path/project/deps.edn -d path/dependency-check.properties` 