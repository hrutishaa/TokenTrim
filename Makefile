.PHONY: test run benchmark

test:
	./mvnw -B verify

run:
	TOKENTRIM_UPSTREAM_MODE=echo ./mvnw -B spring-boot:run

benchmark:
	./mvnw -B -Dtest=TokenSavingsBenchmarkTest -Dsurefire.useFile=false test
