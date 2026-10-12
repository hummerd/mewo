.PHONY: lint

lint:
	./gradlew --continue :app:lintDebug :detekt ktlintCheck
