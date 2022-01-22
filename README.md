# clj-watson
Clojure software composition analysis (SCA).

# Build
`clj -X:depstar`

# Executing
`java -jar target/clj-watson.jar path/project/deps.edn path/dependency-check.properties` 

# Lint fix
```
clj -M:clojure-lsp format
clj -M:clojure-lsp clean-ns
clj -M:clojure-lsp diagnostics
```