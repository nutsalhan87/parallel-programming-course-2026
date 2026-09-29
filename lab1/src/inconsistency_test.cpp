#include <atomic>
#include <cstddef>
#include <cstdint>
#include <future>
#include <iomanip>
#include <iostream>
#include <memory>
#include <thread>
#include <vector>

#include "metrics/metrics.hpp"
#include "metrics/metrics_baseline.hpp"
#include "metrics/metrics_db.hpp"
#include "metrics/metrics_naive.hpp"
#include "metrics/metrics_sharded.hpp"
#include "metrics/metrics_stub.hpp"
#include "metrics/metrics_threadlocal.hpp"
#include "zipf.hpp"

struct InconsistencyTestResult {
    double broken_snapshot_percent;
    uint64_t sum_lt_count;
    uint64_t sum_gt_count;
    uint64_t local_counts_sum;
    uint64_t final_count;
};

InconsistencyTestResult inconsistency_test(
    std::shared_ptr<MetricsCollector>& collector, std::size_t threads,
    std::size_t snapshot_calls)
{
    auto stop = std::atomic_bool(false);
    auto tasks = std::vector<std::thread>(threads);
    auto results = std::vector<std::future<uint64_t>>(threads);
    for (std::size_t i = 0; i < threads; ++i) {
        std::promise<uint64_t> result;
        results[i] = result.get_future();
        tasks[i] = std::thread([i, &stop, collector, result = std::move(result)]() mutable {
            auto gen = zipf::Generator(i);
            uint64_t local_count = 0;
            while (!stop.load(std::memory_order_relaxed)) {
                collector->record(gen.next());
                local_count++;
            }
            result.set_value(local_count);
        });
    }

    uint64_t broken_snapshots = 0;
    uint64_t sum_lt_count = 0;
    uint64_t sum_gt_count = 0;
    for (std::size_t i = 0; i < snapshot_calls; ++i) {
        auto snapshot = collector->snapshot();
        uint64_t buckets_sum = 0;
        for (auto bucket_count : snapshot.buckets) {
            buckets_sum += bucket_count;
        }
        if (buckets_sum != snapshot.count) {
            broken_snapshots++;
            if (buckets_sum > snapshot.count) {
                sum_gt_count++;
            } else {
                sum_lt_count++;
            }
        }
    }
    stop.store(true);
    double broken_snapshot_percent = broken_snapshots * 100. / snapshot_calls;

    uint64_t local_counts_sum = 0;
    for (auto& t : results) {
        local_counts_sum += t.get();
    }
    for (auto& task : tasks) {
        task.join();
    }

    uint64_t final_count = collector->snapshot().count;

    return {
        broken_snapshot_percent,
        sum_lt_count,
        sum_gt_count,
        local_counts_sum,
        final_count
    };
}

int main()
{
    constexpr std::size_t threads = 4;
    constexpr std::size_t snapshot_calls = 10000;

    std::shared_ptr<MetricsCollector> collectors[] = {
        std::make_shared<MetricsCollectorsSharded>(),
        std::make_shared<MetricsCollectorsThreadLocal>(),
        std::make_shared<MetricsCollectorsDB>(),
        std::make_shared<MetricsCollectorsDB>(false)
    };

    for (auto& collector : collectors) {
        auto result = inconsistency_test(collector, threads, snapshot_calls);
        std::cout << collector->name() << " with " << threads << " threads running. Main thread collecting " << snapshot_calls << " snapshots." << std::endl;
        std::cout << "\t" << "Broken snapshots: " << std::setprecision(2) << result.broken_snapshot_percent << "%" << std::endl;
        std::cout << "\t" << "When sum(buckets) < count: " << result.sum_lt_count << std::endl;
        std::cout << "\t" << "When sum(buckets) > count: " << result.sum_gt_count << std::endl;
        std::cout << "\t" << "Local counts sum: " << result.local_counts_sum << std::endl;
        std::cout << "\t" << "Final snapshot count: " << result.final_count << std::endl;
        std::cout << std::endl;
    }

    return 0;
}