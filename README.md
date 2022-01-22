# clj-watson
Clojure software composition analysis (SCA).

# Build
`clj -X:depstar`

# Executing
`java -jar target/clj-watson.jar path/project/deps.edn path/dependency-check.properties` 

# Lint
```
clj -M:lint
clj -M:lint-fix
```