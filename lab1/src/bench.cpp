#include "measurer.hpp"
#include "metrics/metrics.hpp"
#include "metrics/metrics_naive.hpp"
#include "metrics/metrics_sharded.hpp"
#include "metrics/metrics_stub.hpp"
#include "zipf.hpp"
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <iterator>
#include <memory>

int main()
{
    constexpr std::size_t elements = 1 << 20;
    uint64_t seed = 0;
    auto values = zipf::generate(elements, seed);
    std::size_t threads[] = { 1, 2, 4, 6, 12 };

    std::shared_ptr<MetricsCollector> collectors[] = {
        std::make_shared<MetricsCollectorsStub>(),
        std::make_shared<MetricsCollectorsNaive>(),
        std::make_shared<MetricsCollectorsSharded>()
    };

    for (auto& collector : collectors) {
        auto op_secs = std::vector<uint64_t>(std::size(threads));
        for (std::size_t i = 0; i < std::size(threads); ++i) {
            op_secs[i] = measure_point(collector, values, threads[i]);
        }
        draw_histogram(std::cout, threads, op_secs, collector->name(), "Threads",
            "Op/Sec");
    }

    return 0;
}