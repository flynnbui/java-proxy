# COMP3331/9331 HTTP Proxy - Java Implementation
# Makefile for building and running the proxy server

# Java compiler settings
JAVA_HOME ?= /usr/lib/jvm/default-java
JAVAC = javac
JAVA = java

# Project structure
SRC_DIR = src/main/java
BUILD_DIR = build/classes
MAIN_CLASS = com.comp3331.proxy.HttpProxy

# Source files (will be auto-discovered)
SOURCES = $(shell find $(SRC_DIR) -name "*.java")
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(BUILD_DIR)/%.class)

# Compiler flags
JAVAC_FLAGS = -d $(BUILD_DIR) -cp $(SRC_DIR) -Xlint:unchecked

# Default target
all: build

# Create build directory
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Compile Java sources
build: $(BUILD_DIR) $(CLASSES)
	@echo "Build completed successfully"

$(BUILD_DIR)/%.class: $(SRC_DIR)/%.java
	$(JAVAC) $(JAVAC_FLAGS) $<

# Run the proxy server
run: build
	@echo "Usage: make run ARGS='<port> <timeout> <max_object_size> <max_cache_size>'"
	@echo "Example: make run ARGS='50000 10 1048576 10485760'"
	$(JAVA) -cp $(BUILD_DIR) $(MAIN_CLASS) $(ARGS)

# Clean build artifacts
clean:
	rm -rf $(BUILD_DIR)
	@echo "Clean completed"

# Test compilation only
test-compile: build
	@echo "Compilation test passed"

# Format check (placeholder for style checking)
format:
	@echo "Code formatting check - implement with checkstyle if needed"

# Install dependencies (none needed for basic socket programming)
install:
	@echo "No external dependencies required for basic socket implementation"

# Help target
help:
	@echo "Available targets:"
	@echo "  all         - Build the project (default)"
	@echo "  build       - Compile all Java sources"
	@echo "  run         - Run the proxy server (use ARGS to pass arguments)"
	@echo "  clean       - Remove build artifacts"
	@echo "  test-compile - Test compilation without running"
	@echo "  format      - Check code formatting"
	@echo "  install     - Install dependencies (none required)"
	@echo "  help        - Show this help message"
	@echo ""
	@echo "Example usage:"
	@echo "  make build"
	@echo "  make run ARGS='50000 10 1048576 10485760'"

.PHONY: all build run clean test-compile format install help