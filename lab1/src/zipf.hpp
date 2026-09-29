#pragma once

#include <cstdint>
#include <random>
#include <vector>

namespace zipf {
constexpr int MIN_VALUE = 1;
constexpr int MAX_VALUE = 1023;
constexpr double K = 1.15;

class Generator {
public:
    explicit Generator(uint64_t seed);

    // Следующее значение в диапазоне [MIN_VALUE, MAX_VALUE]
    uint64_t next();

private:
    std::mt19937_64 rng_;
    std::discrete_distribution<uint64_t> dist_;
};

std::vector<uint64_t> generate(std::size_t elements, uint64_t seed);
}
