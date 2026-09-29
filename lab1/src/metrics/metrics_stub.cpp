#include "metrics_stub.hpp"

void MetricsCollectorsStub::record(uint64_t value)
{
    auto lock = std::lock_guard<std::mutex>(this->mutex);
}

Snapshot MetricsCollectorsStub::snapshot()
{
    return { {}, 0, 0, 0, 0, 0, 0 };
}

const std::string MetricsCollectorsStub::name()
{
    return "Stub Metrics Collector";
}