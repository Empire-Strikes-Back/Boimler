#!/bin/bash

repl(){
  clj \
    -X:repl deps-repl.core/process \
    :main-ns mult.main \
    :port 7788 \
    :host '"0.0.0.0"' \
    :repl? true \
    :nrepl? false
}

main(){
  clojure \
    -J-Dclojure.core.async.pool-size=1 \
    -J-Dclojure.compiler.direct-linking=false \
    -M -m mult.main
}

"$@"