# clj-watson
Clojure software composition analysis (SCA).

# nREPL
`clj -M:nREPL -m nrepl.cmdline`

# Build
`clj -X:depstar`

# Executing
`java -jar target/clj-watson.jar path/project/deps.edn path/dependency-check.properties` 

# Lint
```
clj -M:lint
clj -M:lint-fix
```