#pragma once

#include "metrics/metrics.hpp"
#include <memory>
#include <span>

uint64_t measure_point(std::shared_ptr<MetricsCollector>& collector,
    std::span<uint64_t> values, std::size_t threads);