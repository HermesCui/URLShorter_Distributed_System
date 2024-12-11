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
PROCESS_NAME = LoadBalancerServer
CMD = "jps | grep -e '${PROCESS_NAME}' | cut -d ' ' -f 1 | xargs -r kill"

all: build

build:
	mkdir -p $(BIN_DIR)
	javac -cp $(LIBS_DIR) -d $(BIN_DIR) $(SRC_DIR)/$(SOURCES) $(LIB_FILES)

#Run the applet locally
run: clean build
	java -cp $(BIN_DIR):$(LIBS_DIR) LoadBalancerServer $(APPLET_DIR) $(CONFIG_DIR)

#Run the applet via an agent. This will not clean the current build as it may be in use by other processes.
agentRun:
	java -cp $(BIN_DIR):$(LIBS_DIR) LoadBalancerServer $(APPLET_DIR) $(CONFIG_DIR) &

clean:
	rm -rf $(BIN_DIR)/**/*

#Run the Applet on the remote servers as defined in host
remote_run:
	echo ${APPLET_DIR}

#Kill all Applets on the remote server as defined in host.
remote_kill:
	echo ${CURR_DIR}
