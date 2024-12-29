#!/bin/bash

HOST=localhost
PORT=59595 # select port of server

echo "
#_(require '[clojure.tools.namespace.repl :refer [refresh]])
#_(refresh)
(require '[asuki.back.core :as core])
(core/db-migrate)
" | clj -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}}}' \
  -M -m nrepl.cmdline \
  --connect --host $HOST --port $PORT