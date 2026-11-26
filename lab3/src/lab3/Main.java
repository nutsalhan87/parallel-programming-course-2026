package lab3;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {
    private static Schedule schedule;

    private static Map<Integer, SortedSet<Flight>> airportToFlights = new HashMap<>();
    private static Map<FromTo, SortedSet<Flight>> fromToFlights = new HashMap<>();

    private static record FromTo(Integer from, Integer to) {}
    private static record Flight(int id, int airlineId, int fromId, int toId, int dep, int arr, int price) {
        public Flight(int flightId, Schedule schedule) {
            this(flightId, 
                schedule.airline(flightId), 
                schedule.from(flightId), 
                schedule.to(flightId), 
                schedule.dep(flightId), 
                schedule.arr(flightId), 
                schedule.price(flightId));
        }
    }

    private static Optional<List<String>> argParse(String key, int n, String[] args) {
        List<String> parsed = new ArrayList<>(n);

        int keyIdx = -1;
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals(key)) {
                keyIdx = i;
                break;
            }
        }

        if (keyIdx == -1 || keyIdx + n >= args.length) {
            return Optional.empty();
        }

        for (int i = keyIdx + 1; i <= keyIdx + n; ++i) {
            parsed.add(args[i]);
        }

        return Optional.of(parsed);
    }

    public static void main(String[] args) {
        var scheduleBin = argParse("--schedule-bin", 1, args)
            .map(List::getFirst)
            .orElse("./data/schedule.bin");
        try {
            schedule = Schedule.open(Path.of(scheduleBin));
        } catch (IOException e) {
            System.err.println("Error opening schedule file: " + e);
            System.exit(1);
        }

        var start = Instant.now();
        for (int flightId = 0; flightId < schedule.nFlights; ++flightId) {
            var flight = new Flight(flightId, schedule);

            var flights = airportToFlights.computeIfAbsent(
                flight.fromId(), 
                f -> new TreeSet<>(Comparator.comparingInt(Flight::dep).thenComparing(Flight::id))
            );
            flights.add(flight);
            
            var fromTo = new FromTo(flight.fromId, flight.toId);
            var fromToFlightsSet = fromToFlights.computeIfAbsent(
                fromTo,
                f -> new TreeSet<>(Comparator.comparingInt(Flight::dep).thenComparing(Flight::id))
            );
            fromToFlightsSet.add(flight);
        }
        var end = Instant.now();
        System.out.println("Index was built for " + (end.toEpochMilli() - start.toEpochMilli()) + " ms");

        var queries = argParse("--queries", 1, args)
            .map(List::getFirst)
            .orElse("./data/queries.txt");
        var method = argParse("--method", 1, args)
            .map(List::getFirst)
            .map(m -> {
                switch (m) {
                    case "fork-join":
                        return new Top10.ForkJoin();
                    case "baseline":
                    default:
                        return new Top10.Baseline();
                }
            })
            .orElseGet(Top10.Baseline::new);
        try (BufferedReader reader = Files.newBufferedReader(Path.of(queries))) {
            var cnt = new AtomicInteger(1);
            reader.lines().map(query -> {
                var splitted = query.split("\s");
                return method.top10(splitted[0], splitted[1], Integer.parseUnsignedInt(splitted[2]));
            }).forEachOrdered(top10 -> {
                var idx = cnt.getAndIncrement();
                System.out.printf("Q%d %s %s %d count=%d ms=%d\n", idx, top10.from, top10.to, top10.day, top10.count, top10.ms);
                top10.top10.forEach(route -> {
                    var path = route.route.stream()
                        .map(Flight::id)
                        .map(v -> v.toString())
                        .collect(Collectors.joining("-"));
                    System.out.printf("  %d %d %s\n", route.sum, route.speed, path);
                });
            });
        } catch (IOException e) {
            System.err.println("Error opening queries file: " + e);
            System.exit(2);
        }
    }

    private static sealed interface Top10 permits Top10.Baseline, Top10.ForkJoin {
        Top10Result top10(String from, String to, int day);
        
        public static final class Baseline implements Top10 {
            private void top10Inner(List<Flight> route, int from, int to, int day, SortedSet<Route> boundedTop10, AtomicInteger flightCounter) {
                if (!route.isEmpty() && route.getLast().toId() == to) {
                    var sum = route.stream().mapToInt(Flight::price).sum();
                    var speed = route.getLast().arr() - route.getFirst().dep();
                    var routeObj = new Route(route, sum, speed);
                    if (boundedTop10.isEmpty() || boundedTop10.comparator().compare(boundedTop10.last(), routeObj) > 0) {
                        boundedTop10.add(routeObj);
                    }
                    flightCounter.incrementAndGet();
                    return;
                }

                if (route.size() >= 3) {
                    return;
                }

                int depLeftBound, depRightBound;
                if (route.isEmpty()) {
                    depLeftBound = day * 1440;
                    depRightBound = (day + 1) * 1440;
                } else {
                    depLeftBound = route.getLast().arr() + 60;
                    depRightBound = route.getLast().arr() + 361;
                }
                var startDummy = new Flight(Integer.MIN_VALUE, 0, 0, 0, depLeftBound, 0, 0);
                var endDummy = new Flight(Integer.MIN_VALUE, 0, 0, 0, depRightBound, 0, 0);

                var airportId = route.isEmpty() ? from : route.getLast().toId();
                SortedSet<Flight> flights;
                if (route.size() == 2) {
                    var fromTo = new FromTo(airportId, to);
                    flights = fromToFlights.get(fromTo);
                } else {
                    flights = airportToFlights.get(airportId);
                }
                if (flights == null || flights.isEmpty()) {
                    return;
                }
                
                outer:
                for (var flight : flights.subSet(startDummy, endDummy)) {
                    for (var flightInRoute : route) {
                        if (flightInRoute.fromId == flight.toId) {
                            continue outer;
                        }
                    }
                    route.addLast(flight);
                    top10Inner(route, from, to, day, boundedTop10, flightCounter);
                    route.removeLast();
                }
            } 

            public Top10Result top10(String from, String to, int day) {
                SortedSet<Route> boundedTop10 = boundedTop10Set();
                var fromId = schedule.airportId(from);
                var toId = schedule.airportId(to);
                var flightCounter = new AtomicInteger(0);
                var start = Instant.now();
                top10Inner(new ArrayList<>(3), fromId, toId, day, boundedTop10, flightCounter);
                var end = Instant.now();
                return new Top10Result(boundedTop10, from, to, day, flightCounter.get(), end.toEpochMilli() - start.toEpochMilli());
            }
        }

        public static final class ForkJoin implements Top10 {
            public Top10Result top10(String from, String to, int day) {
                return new Top10Result(boundedTop10Set(), "from", "to", 0, 0, 0);
            }
        }

        public static record Top10Result(SortedSet<Route> top10, String from, String to, int day, long count, long ms) {}

        private static SortedSet<Route> boundedTop10Set() {
            return new TreeSet<>((r1, r2) -> {
                if (r1.sum != r2.sum) {
                    return r1.sum - r2.sum;
                }

                if (r1.speed != r2.speed) {
                    return r1.speed - r2.speed;
                }

                for (int i = 0; i < Math.min(r1.route.size(), r2.route.size()); ++i) {
                    var id1 = r1.route.get(i).id();
                    var id2 = r2.route.get(i).id();
                    if (id1 != id2) {
                        return id1 - id2;
                    }
                }

                return r1.route.size() - r2.route.size();
            }) {
                @Override
                public boolean add(Route route) {
                    super.add(route);
                    if (this.size() > 10) {
                        var removed = this.removeLast();
                        if (removed == route) {
                            return false;
                        }
                    }
                    return true;
                };
            };
        }

        public static record Route(List<Flight> route, int sum, int speed) {}
    }
}
