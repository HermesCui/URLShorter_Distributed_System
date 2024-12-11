# Directories
CURR_DIR = $(shell pwd)
SRC_DIR = $(shell realpath .)
BIN_DIR = bin
LIBS_DIR = $(shell realpath ../../libs)
CONFIG_DIR = $(shell realpath ../../config)
TOOL_DIR = $(shell realpath ../../scripts)
APPLET_DIR = $(shell realpath ..)

# File Locations
SOURCES = $(wildcard *.java)
LIB_FILES = $(wildcard $(LIBS_DIR)/**/*.java)
HTML_ASSETS = index.html

# SQLite JDBC Dependency
SCHEMA_FILE = schema.sql

# Process Control
PROCESS_NAME = ManagementPlaneServer
CMD = "jps | grep -e '${PROCESS_NAME}' | cut -d ' ' -f 1 | xargs -r kill"

all: build


# Compile the Java sources, including libraries and SQLite JDBC
build: copy_html
	mkdir -p $(BIN_DIR)
	javac -cp $(LIBS_DIR) -d $(BIN_DIR) $(SRC_DIR)/$(SOURCES) $(LIB_FILES)


# Copy HTML assets to the bin directory
copy_html:
	mkdir -p $(BIN_DIR)
	cp $(HTML_ASSETS) $(BIN_DIR)

# Run the applet locally, ensuring the SQLite database is setup
run: clean build
	java -cp $(BIN_DIR):$(LIBS_DIR) ManagementPlaneServer $(APPLET_DIR) $(CONFIG_DIR)

start:
	java -cp $(BIN_DIR):$(LIBS_DIR) ManagementPlaneServer $(APPLET_DIR) $(CONFIG_DIR)


# Run the applet via an agent without cleaning the current build
agentRun:
	java -cp $(BIN_DIR):$(LIBS_DIR) ManagementPlaneServer $(APPLET_DIR) $(CONFIG_DIR) &

# Clean the build directory
clean:
	rm -rf $(BIN_DIR)/**/*

# Run the applet on the remote servers as defined in host
remote_run:
	echo ${APPLET_DIR}

# Kill all applets on the remote server as defined in host
remote_kill:
	echo ${CURR_DIR}
