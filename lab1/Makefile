rwildcard = $(foreach dir,$(wildcard $1*/),$(call rwildcard,$(dir),$2)) \
            $(wildcard $1$2)

CXX      := g++
CXXFLAGS := -O2 -std=c++20 -pthread

SOURCES := $(call rwildcard,src/,*.cpp)

BENCH_SRC := src/bench.cpp
TEST_SRC  := src/inconsistency_test.cpp

COMMON_SRCS := $(filter-out $(BENCH_SRC) $(TEST_SRC),$(SOURCES))

BUILD_DIR := build
OBJ_DIR   := $(BUILD_DIR)/obj

COMMON_OBJS := $(patsubst %.cpp,$(OBJ_DIR)/%.o,$(COMMON_SRCS))
BENCH_OBJ   := $(OBJ_DIR)/$(BENCH_SRC:.cpp=.o)
TEST_OBJ    := $(OBJ_DIR)/$(TEST_SRC:.cpp=.o)

BENCH_BIN := $(BUILD_DIR)/bench
TEST_BIN  := $(BUILD_DIR)/inconsistency_test

.PHONY: build
build: $(BENCH_BIN) $(TEST_BIN)

$(BENCH_BIN): $(COMMON_OBJS) $(BENCH_OBJ)
	@mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) $^ -o $@

$(TEST_BIN): $(COMMON_OBJS) $(TEST_OBJ)
	@mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) $^ -o $@

# Compile any source file into a matching object file under build/obj/.
$(OBJ_DIR)/%.o: %.cpp
	@mkdir -p $(@D)
	$(CXX) $(CXXFLAGS) -MMD -MP -c $< -o $@

.PHONY: bench
bench: $(BENCH_BIN)
	./$(BENCH_BIN)

.PHONY: test
test: $(TEST_BIN)
	./$(TEST_BIN)


# ThreadSanitizer builds
TSAN_CXX      := clang++
TSAN_CXXFLAGS := -fsanitize=thread -g -O1 -std=c++20 -pthread

TSAN_OBJ_DIR := $(BUILD_DIR)/obj-tsan

TSAN_COMMON_OBJS := $(patsubst %.cpp,$(TSAN_OBJ_DIR)/%.o,$(COMMON_SRCS))
TSAN_BENCH_OBJ   := $(TSAN_OBJ_DIR)/$(BENCH_SRC:.cpp=.o)
TSAN_TEST_OBJ    := $(TSAN_OBJ_DIR)/$(TEST_SRC:.cpp=.o)

TSAN_BENCH_BIN := $(BUILD_DIR)/bench_tsan
TSAN_TEST_BIN  := $(BUILD_DIR)/inconsistency_test_tsan

.PHONY: build_tsan
build_tsan: $(TSAN_BENCH_BIN) $(TSAN_TEST_BIN)

$(TSAN_BENCH_BIN): $(TSAN_COMMON_OBJS) $(TSAN_BENCH_OBJ)
	@mkdir -p $(@D)
	$(TSAN_CXX) $(TSAN_CXXFLAGS) $^ -o $@

$(TSAN_TEST_BIN): $(TSAN_COMMON_OBJS) $(TSAN_TEST_OBJ)
	@mkdir -p $(@D)
	$(TSAN_CXX) $(TSAN_CXXFLAGS) $^ -o $@

$(TSAN_OBJ_DIR)/%.o: %.cpp
	@mkdir -p $(@D)
	$(TSAN_CXX) $(TSAN_CXXFLAGS) -MMD -MP -c $< -o $@

.PHONY: bench_tsan
bench_tsan: $(TSAN_BENCH_BIN)
	./$(TSAN_BENCH_BIN)

.PHONY: test_tsan
test_tsan: $(TSAN_TEST_BIN)
	./$(TSAN_TEST_BIN)


.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)

-include $(shell find $(BUILD_DIR) -name '*.d' 2>/dev/null)
