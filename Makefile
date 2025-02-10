# Assumes shared configs and secrets!

fmt:
	clojure-lsp format

lint:
	clojure-lsp diagnostics

prep-resources:
	lein run -m omega-red.gen-cmd-config

test: prep-resources
	lein test


deploy: prep-resources
	lein deploy clojars
