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


deploy: prep-resources
	lein deploy clojars
