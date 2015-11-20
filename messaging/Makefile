# EE 382N: Distributed Systems - Term Paper

SHELL := /bin/bash

CLASSPATH := .

CLASS_PREFIXES := 
CLASS_PREFIXES += Client
CLASS_PREFIXES += Server

# first rule is built by default when "make" runs
all: | $(foreach PREFIX,$(CLASS_PREFIXES),$(PREFIX).class)

# build source code
%.class: %.java
	javac -cp $(CLASSPATH) $^

run: all

# remove built class files
clean:
	@# remove public classes
	$(foreach PREFIX,$(CLASS_PREFIXES),rm -f $(PREFIX).class)
	@# remove inner classes:
	rm -f *$$*.class
	@# remove temporary files (marked as tmp-*)
	rm -f tmp-*

# tell "make" not to treat these as file names:
.PHONY: all run clean
