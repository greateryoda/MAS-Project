CP  = lib/*
SRC = $(wildcard src/*.java)

.PHONY: all build run clean

all: run

build: $(SRC)
	mkdir -p bin
	javac -cp "$(CP)" -d bin $(SRC)

run: build
	java -cp "bin:$(CP)" Main

clean:
	rm -rf bin
