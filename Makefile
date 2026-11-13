CP  = lib/*
SRC = $(wildcard src/*.java)
TESTS = $(wildcard test/*.java)
WORKSPACE = build/test-workspace

# separateur du classpath : ';' sous Windows, ':' ailleurs
ifeq ($(OS),Windows_NT)
  SEP = ;
else
  SEP = :
endif

.PHONY: all build run gui console test eval-routing eval-replies eval-log clean

all: run

build: $(SRC)
	mkdir -p bin
	javac --release 17 -cp "$(CP)" -d bin $(SRC)

run: gui

gui: build
	java -cp "bin$(SEP)$(CP)" App

console: build
	java -cp "bin$(SEP)$(CP)" Main

test: build
	rm -rf $(WORKSPACE) bin-test
	mkdir -p $(WORKSPACE) bin-test
	cp -r agents critic demo eval $(WORKSPACE)/
	javac --release 17 -cp "bin$(SEP)$(CP)" -d bin-test $(TESTS)
	cd $(WORKSPACE) && java -cp "../../bin$(SEP)../../bin-test$(SEP)../../lib/*" ScenarioTests

# experiences avec le vrai LLM local (LM Studio demarre) ; rapports dans eval/results/
eval-routing: build
	java -cp "bin$(SEP)$(CP)" Evaluation routing $(ARGS)

eval-replies: build
	java -cp "bin$(SEP)$(CP)" Evaluation replies $(ARGS)

eval-log: build
	java -cp "bin$(SEP)$(CP)" Evaluation log $(ARGS)

clean:
	rm -rf bin bin-test build
