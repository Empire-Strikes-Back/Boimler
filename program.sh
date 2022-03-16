#!/bin/bash

clean(){
    rm -rf out/js-out out .shadow-cljs .cpcache 
}

repl(){
  clj -A:repl
}

shadow(){
    ./node_modules/.bin/shadow-cljs -A:core:shadow-cljs:main:ui-main "$@"
}

dev(){
    npm i
    shadow watch :main :ui-main

}

compile(){
    npm i
    shadow compile :main :ui-main
}

build(){
    rm -rf out
    npm i
    mkdir -p out
    cp src/Boimler/index.html out
    cp src/Boimler/style.css out
    shadow release :main :ui-main
}


cljs_compile(){
    clj -m cljs.main -co cljs-build.edn -c
    #  clj -A:dev -m cljs.main -co cljs-build.edn -v -c # -r
}

release(){
  rm -rf out/*.vsix
  npx vsce package --out "out/Boimler-$(git rev-parse --short HEAD).vsix"
}

server(){
    shadow server
}

"$@"