CP  = lib/*
SRC = $(wildcard src/*.java)
TESTS = $(wildcard test/*.java)
WORKSPACE = build/test-workspace

.PHONY: all build run gui console test clean

all: run

build: $(SRC)
	mkdir -p bin
	javac --release 17 -cp "$(CP)" -d bin $(SRC)

run: gui

gui: build
	java -cp "bin:$(CP)" App

console: build
	java -cp "bin:$(CP)" Main

test: build
	rm -rf $(WORKSPACE) bin-test
	mkdir -p $(WORKSPACE) bin-test
	cp -r agents critic demo $(WORKSPACE)/
	javac --release 17 -cp "bin:$(CP)" -d bin-test $(TESTS)
	cd $(WORKSPACE) && java -cp "../../bin:../../bin-test:../../lib/*" ScenarioTests

clean:
	rm -rf bin bin-test build
