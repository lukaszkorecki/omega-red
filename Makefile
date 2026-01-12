# Assumes shared configs and secrets!

fmt:
	clojure-lsp format

lint:
	clojure-lsp diagnostics

prep-resources:
	lein run -m omega-red.gen-cmd-config
	cat resources/redis-commands.edn  | jet -i edn  > /tmp/f.edn
	mv /tmp/f.edn resources/redis-commands.edn

test: prep-resources
	lein test

clean:
	lein clean

deploy: prep-resources
	lein deploy clojars


help:
	grep -E '^[a-z]+:' ./Makefile

.PHONY: fmt lint prep-resources test deploy help clean