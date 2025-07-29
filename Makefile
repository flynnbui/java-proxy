# Makefile for HTTP Proxy - Production Build
# Builds the HTTP proxy implementation without tests

# Directories
SRC_DIR = src/main/java
BUILD_DIR = build

# Java settings
JAVAC = javac
JAVA = java

# Main class
MAIN_CLASS = proxy.Proxy

# Source files
MAIN_SOURCES = $(shell find $(SRC_DIR) -name "*.java")

# Default target
.PHONY: all
all: build

# Create necessary directories
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Compile main source code
.PHONY: build
build: $(BUILD_DIR)
	@echo "Compiling HTTP Proxy..."
	$(JAVAC) -d $(BUILD_DIR) -cp $(BUILD_DIR) $(MAIN_SOURCES)
	@echo "Build completed successfully!"

# Run the proxy (example usage)
.PHONY: run
run: build
	@echo "Usage: make run PORT=<port> TIMEOUT=<timeout> MAX_OBJ=<max_obj_size> MAX_CACHE=<max_cache_size>"
	@echo "Example: make run PORT=8080 TIMEOUT=10 MAX_OBJ=1024 MAX_CACHE=8192"
ifdef PORT
	$(JAVA) -cp $(BUILD_DIR) $(MAIN_CLASS) $(PORT) $(TIMEOUT) $(MAX_OBJ) $(MAX_CACHE)
else
	@echo "Error: Please specify PORT, TIMEOUT, MAX_OBJ, and MAX_CACHE parameters"
endif

# Check if source files exist
.PHONY: validate
validate:
	@echo "Validating source files..."
	@echo "Java compiler: $$(which $(JAVAC) 2>/dev/null || echo 'NOT FOUND')"
	@echo "Main sources found: $$(echo $(MAIN_SOURCES) | wc -w) files"
	@if [ -f "$(SRC_DIR)/$(shell echo $(MAIN_CLASS) | tr '.' '/').java" ]; then \
		echo "Main class found: ✓"; \
	else \
		echo "Main class NOT found: ✗"; \
	fi

# Clean build artifacts
.PHONY: clean
clean:
	@echo "Cleaning build artifacts..."
	rm -rf $(BUILD_DIR)

# Help target
.PHONY: help
help:
	@echo "HTTP Proxy Makefile"
	@echo "==================="
	@echo ""
	@echo "Targets:"
	@echo "  all       - Build the proxy (default)"
	@echo "  build     - Compile source code"
	@echo "  run       - Run the proxy (requires PORT, TIMEOUT, MAX_OBJ, MAX_CACHE)"
	@echo "  validate  - Check build requirements"
	@echo "  clean     - Clean build artifacts"
	@echo "  help      - Show this help message"
	@echo ""
	@echo "Examples:"
	@echo "  make build                                    # Compile the proxy"
	@echo "  make run PORT=8080 TIMEOUT=10 MAX_OBJ=1024 MAX_CACHE=8192"