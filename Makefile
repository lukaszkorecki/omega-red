# Assumes shared configs and secrets!

fmt:
	clojure-lsp format

lint:
	clojure-lsp diagnostics


deploy:
	lein deploy clojars
