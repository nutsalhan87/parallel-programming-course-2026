#include "measurer.hpp"
#include "metrics/metrics.hpp"
#include "metrics/metrics_baseline.hpp"
#include "metrics/metrics_db.hpp"
#include "metrics/metrics_naive.hpp"
#include "metrics/metrics_sharded.hpp"
#include "metrics/metrics_stub.hpp"
#include "metrics/metrics_threadlocal.hpp"
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
        std::make_shared<MetricsCollectorsBaseline>(),
        std::make_shared<MetricsCollectorsStub>(),
        std::make_shared<MetricsCollectorsNaive>(),
        std::make_shared<MetricsCollectorsSharded>(),
        std::make_shared<MetricsCollectorsThreadLocal>(),
        std::make_shared<MetricsCollectorsDB>()
    };

    for (auto& collector : collectors) {
        auto op_secs_size = collector->name().rfind("Baseline", 0) == 0 ? 1 : std::size(threads); 
        auto op_secs = std::vector<uint64_t>(op_secs_size);
        for (std::size_t i = 0; i < op_secs_size; ++i) {
            op_secs[i] = measure_point(collector, values, threads[i]);
        }
        draw_histogram(std::cout, threads, op_secs, collector->name(), "Threads",
            "Op/Sec");
        
        std::cout << "| threads | ";
        for (std::size_t i = 0; i < std::size(threads); ++i) {
            std::cout << threads[i] << " | ";
        }
        std::cout << std::endl << "| mil op/sec | ";
        for (std::size_t i = 0; i < std::size(op_secs); ++i) {
            std::cout << op_secs[i] / 1000000 << " | ";
        }
        std::cout << std::endl;
    }

    return 0;
}