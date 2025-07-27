# Makefile for Java HTTP Proxy

# Java compiler
JC = javac
# Java runtime
JAVA = java

# Directories
SRC_DIR = src/main/java
BUILD_DIR = build
OUT_DIR = out

# Main class
MAIN_CLASS = proxy.HttpProxy

# Find all Java source files
SOURCES := $(shell find $(SRC_DIR) -name "*.java" 2>/dev/null || powershell -Command "Get-ChildItem -Path $(SRC_DIR) -Filter '*.java' -Recurse | ForEach-Object { $$_.FullName.Replace('\\', '/').Replace('E:/code/comp3331/java-proxy/', '') }")

# Default target
all: build

# Create build directory
$(BUILD_DIR):
	@echo "Creating build directory..."
	@mkdir -p $(BUILD_DIR) 2>/dev/null || powershell -Command "New-Item -ItemType Directory -Path $(BUILD_DIR) -Force | Out-Null"

# Build the project
build: $(BUILD_DIR)
	@echo "Compiling Java proxy..."
	@$(JC) -d $(BUILD_DIR) -cp $(SRC_DIR) $(SRC_DIR)/proxy/*.java $(SRC_DIR)/proxy/*/*.java
	@echo "Build complete!"

# Alternative build to out directory (for compatibility)
build-out:
	@echo "Creating out directory..."
	@mkdir -p $(OUT_DIR) 2>/dev/null || powershell -Command "New-Item -ItemType Directory -Path $(OUT_DIR) -Force | Out-Null"
	@echo "Compiling to out directory..."
	@$(JC) -d $(OUT_DIR) -cp $(SRC_DIR) $(SRC_DIR)/proxy/*.java $(SRC_DIR)/proxy/*/*.java
	@echo "Build complete!"

# Run the proxy with default settings
run: build
	@echo "Starting HTTP proxy on port 8080..."
	@$(JAVA) -cp $(BUILD_DIR) $(MAIN_CLASS) 8080 30 102400 1048576

# Run with custom port
run-port: build
	@echo "Usage: make run-port PORT=<port>"
	@$(JAVA) -cp $(BUILD_DIR) $(MAIN_CLASS) $(PORT) 30 102400 1048576

# Run with all custom parameters
run-custom: build
	@echo "Usage: make run-custom PORT=<port> TIMEOUT=<timeout> MAX_OBJ=<max_object_size> MAX_CACHE=<max_cache_size>"
	@$(JAVA) -cp $(BUILD_DIR) $(MAIN_CLASS) $(PORT) $(TIMEOUT) $(MAX_OBJ) $(MAX_CACHE)

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR) $(OUT_DIR) 2>/dev/null || powershell -Command "Remove-Item -Path $(BUILD_DIR),$(OUT_DIR) -Recurse -Force -ErrorAction SilentlyContinue"
	@echo "Clean complete!"

# Run tests
test: build
	@echo "Running tests..."
	@python testScript/test_comprehensive.py

test-localhost: build
	@echo "Running localhost test..."
	@python testScript/test_localhost.py

test-invalid-host: build
	@echo "Running invalid host test..."
	@python testScript/test_invalid_host_integrated.py

test-persistent: build
	@echo "Running persistent connection test..."
	@python testScript/test_persistent_localhost.py

# Show help
help:
	@echo "Available targets:"
	@echo "  make              - Build the project"
	@echo "  make build        - Build the project"
	@echo "  make build-out    - Build to 'out' directory"
	@echo "  make run          - Run proxy with default settings (port 8080)"
	@echo "  make run-port PORT=<port> - Run proxy on specific port"
	@echo "  make run-custom PORT=<p> TIMEOUT=<t> MAX_OBJ=<o> MAX_CACHE=<c>"
	@echo "  make clean        - Remove build artifacts"
	@echo "  make test         - Run comprehensive tests"
	@echo "  make test-localhost - Run localhost test"
	@echo "  make test-invalid-host - Run invalid host test"
	@echo "  make test-persistent - Run persistent connection test"
	@echo "  make help         - Show this help message"

.PHONY: all build build-out run run-port run-custom clean test test-localhost test-invalid-host test-persistent help
