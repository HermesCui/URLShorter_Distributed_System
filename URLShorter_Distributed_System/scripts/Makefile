CURR_DIR = $(shell pwd)
SRC_DIR = $(shell realpath .)
BIN_DIR = bin
LIBS_DIR = $(shell realpath ../../libs)
CONFIG_DIR = $(shell realpath ../../config)
TOOL_DIR = $(shell realpath ../../scripts)
SOURCES = $(wildcard *.java)
APPLET_DIR = $(shell realpath ../apps/)



#Remote Kill 
PROCESS_NAME = TestLib
CMD = "jps | grep -e '${PROCESS_NAME}' | cut -d ' ' -f 1 | xargs -r kill"

all: build

build:
	mkdir -p $(BIN_DIR)
	javac -cp $(LIBS_DIR) -d $(BIN_DIR) $(SRC_DIR)/$(SOURCES) $(LIB_FILES)

