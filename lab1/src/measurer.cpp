#include "measurer.hpp"
#include <algorithm>
#include <atomic>
#include <future>
#include <latch>
#include <vector>

uint64_t run(std::shared_ptr<MetricsCollector>& collector,
    std::span<uint64_t> values, std::size_t threads,
    uint64_t sleep_seconds)
{
    auto start = std::latch(1);
    auto stop = std::atomic_bool(false);

    auto tasks = std::vector<std::thread>(threads);
    auto results = std::vector<std::future<uint64_t>>(threads);
    for (std::size_t k = 0; k < threads; ++k) {
        std::promise<uint64_t> result;
        results[k] = result.get_future();
        tasks[k] = std::thread([&, result = std::move(result), k, collector]() mutable {
            uint64_t local_count = 0;
            auto i = k + 1000;
            start.wait();
            while (!stop.load()) {
                collector->record(values[i % values.size()]);
                local_count++;
                i++;
            };
            result.set_value(local_count);
        });
    }

    using namespace std::chrono_literals;
    auto t0 = std::chrono::steady_clock::now();
    start.count_down();
    std::this_thread::sleep_for(std::chrono::seconds(sleep_seconds));
    stop.store(true);
    auto t1 = std::chrono::steady_clock::now();
    uint64_t time_diff = std::chrono::duration_cast<std::chrono::seconds>(t1 - t0).count();

    uint64_t sum = 0;
    for (auto& t : results) {
        sum += t.get();
    }
    for (auto& task : tasks) {
        task.join();
    }

    return sum / time_diff;
}

uint64_t measure_point(std::shared_ptr<MetricsCollector>& collector,
    std::span<uint64_t> values, std::size_t threads)
{
    constexpr uint64_t sleep_seconds = 2;
    run(collector, values, threads, sleep_seconds);
    auto results = std::vector<uint64_t>();
    for (std::size_t i = 0; i < 5; ++i) {
        results.push_back(run(collector, values, threads, sleep_seconds));
    }
    std::nth_element(results.begin(), results.begin() + results.size() / 2,
        results.end());
    return results[results.size() / 2];
}