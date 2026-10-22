#include "zipf.hpp"
#include <random>
#include <vector>

std::discrete_distribution<uint64_t> make_distribution()
{
    constexpr std::size_t n = zipf::MAX_VALUE - zipf::MIN_VALUE + 1;
    std::vector<double> weights(n);
    for (std::size_t i = 0; i < n; ++i)
        weights[i] = 1.0 / std::pow(static_cast<double>(zipf::MIN_VALUE + i), zipf::K);
    return std::discrete_distribution<uint64_t>(weights.begin(), weights.end());
}

zipf::Generator::Generator(uint64_t seed)
    : rng_(seed)
    , dist_(make_distribution())
{
}

uint64_t zipf::Generator::next()
{
    return zipf::MIN_VALUE + dist_(rng_);
}

std::vector<uint64_t> zipf::generate(std::size_t elements, uint64_t seed)
{
    Generator gen(seed);

    std::vector<uint64_t> values(elements);
    for (auto& v : values)
        v = gen.next();
    return values;
}