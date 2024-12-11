CURR_DIR = $(shell pwd)
SRC_DIR = $(shell realpath .)
BIN_DIR = bin
LIBS_DIR = $(shell realpath ../../libs)
CONFIG_DIR = $(shell realpath ../../config)
TOOL_DIR = $(shell realpath ../../scripts)
SOURCES = $(wildcard *.java)

APPLET_DIR = $(shell realpath ..)

LIB_FILES = $(wildcard $(LIBS_DIR)/**/*.java)

#Remote Kill 
PROCESS_NAME = TestLib
CMD = "jps | grep -e '${PROCESS_NAME}' | cut -d ' ' -f 1 | xargs -r kill"

# SQLite JDBC Dependency
SQLITE_JDBC_JAR = sqlite-jdbc-3.39.3.0.jar
SQLITE_DB = /virtual/${USER}/URLShortner/db/KVStore.db
SCHEMA_FILE = schema.sql

all: build

build:
	mkdir -p $(BIN_DIR)
	javac -cp $(LIBS_DIR) -d $(BIN_DIR) $(SRC_DIR)/$(SOURCES) $(LIB_FILES)

#Run the applet locally
run: clean setup_db build
	java -cp $(BIN_DIR):$(LIBS_DIR):$(SQLITE_JDBC_JAR) TestLib $(APPLET_DIR) $(CONFIG_DIR)

setup_db:
	install -d /virtual/${USER}/URLShortner/db/
	rm -f $(SQLITE_DB)
	sqlite3 $(SQLITE_DB) < $(SCHEMA_FILE)

start:
	java -cp $(BIN_DIR):$(LIBS_DIR):$(SQLITE_JDBC_JAR) TestLib $(APPLET_DIR) $(CONFIG_DIR)

#Run the applet via an agent. This will not clean the current build as it may be in use.
agentRun:
	java -cp $(BIN_DIR):$(LIBS_DIR) TestLib $(APPLET_DIR) $(CONFIG_DIR)

clean:
	rm -rf $(BIN_DIR)/**/*

#Run the Applet on the remote servers as defined in host
remote_run:
	echo ${APPLET_DIR}

#Kill all Applets on the remote server as defined in host.
remote_kill:
	echo ${CURR_DIR}
