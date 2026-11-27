package lab3;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
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
                top10.top10.forEach(flights -> {
                    var sum = Arrays.stream(flights).mapToInt(Flight::price).sum();
                    var speed = flights[flights.length - 1].arr() - flights[0].dep();
                    var path = Arrays.stream(flights)
                        .map(Flight::id)
                        .map(v -> v.toString())
                        .collect(Collectors.joining("-"));
                    System.out.printf("  %d %d %s\n", sum, speed, path);
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
            private static class Context {
                List<Flight> flights;
                int from;
                int to;
                int day;
                SortedSet<Route> boundedTop10;
                AtomicInteger flightCounter;
                BitSet visited;
                AtomicInteger sumAcc;
                Route worst;

                public Context(int from, int to, int day) {
                    this.flights = new ArrayList<>(3);
                    this.from = from; 
                    this.to = to;
                    this.day = day;
                    this.boundedTop10 = boundedTop10Set(); 
                    this.flightCounter = new AtomicInteger(0);
                    this.visited = new BitSet(schedule.nAirports);
                    this.sumAcc = new AtomicInteger(0);
                    this.worst = null;
                }
            }

            private void top10Inner(Context ctx) {
                if (!ctx.flights.isEmpty() && ctx.flights.getLast().toId() == ctx.to) {
                    if (ctx.boundedTop10.size() < 10 || (ctx.worst != null && ctx.worst.compareTo(ctx.flights, ctx.sumAcc.get()) > 0)) {
                        var route = Route.from(ctx.flights, ctx.sumAcc.get());
                        ctx.boundedTop10.add(route);
                        ctx.worst = ctx.boundedTop10.last();
                    }
                    ctx.flightCounter.incrementAndGet();
                    return;
                }

                var airportId = ctx.flights.isEmpty() ? ctx.from : ctx.flights.getLast().toId();
                if (ctx.visited.get(airportId)) {
                    return;
                }

                if (ctx.flights.size() >= 3) {
                    return;
                }

                int depLeftBound, depRightBound;
                if (ctx.flights.isEmpty()) {
                    depLeftBound = ctx.day * 1440;
                    depRightBound = (ctx.day + 1) * 1440;
                } else {
                    depLeftBound = ctx.flights.getLast().arr() + 60;
                    depRightBound = ctx.flights.getLast().arr() + 361;
                }
                var startDummy = new Flight(Integer.MIN_VALUE, 0, 0, 0, depLeftBound, 0, 0);
                var endDummy = new Flight(Integer.MIN_VALUE, 0, 0, 0, depRightBound, 0, 0);

                SortedSet<Flight> fromFlights;
                if (ctx.flights.size() == 2) {
                    var fromTo = new FromTo(airportId, ctx.to);
                    fromFlights = fromToFlights.get(fromTo);
                } else {
                    fromFlights = airportToFlights.get(airportId);
                }
                if (fromFlights == null || fromFlights.isEmpty()) {
                    return;
                }
                
                for (var flight : fromFlights.subSet(startDummy, endDummy)) {
                    ctx.flights.addLast(flight);
                    ctx.visited.set(airportId);
                    ctx.sumAcc.addAndGet(flight.price);
                    top10Inner(ctx);
                    ctx.flights.removeLast();
                    ctx.visited.clear(airportId);
                    ctx.sumAcc.addAndGet(-flight.price);
                }
            } 

            public Top10Result top10(String from, String to, int day) {
                var fromId = schedule.airportId(from);
                var toId = schedule.airportId(to);
                var ctx = new Context(fromId, toId, day);
                var start = Instant.now();
                top10Inner(ctx);
                var end = Instant.now();
                var top10 = ctx.boundedTop10.stream().map(Route::getFlights).toList();
                return new Top10Result(top10, from, to, day, ctx.flightCounter.get(), end.toEpochMilli() - start.toEpochMilli());
            }
        }

        public static final class ForkJoin implements Top10 {
            public Top10Result top10(String from, String to, int day) {
                return new Top10Result(List.of(), "from", "to", 0, 0, 0);
            }
        }

        public static record Top10Result(List<Flight[]> top10, String from, String to, int day, long count, long ms) {}

        private static SortedSet<Route> boundedTop10Set() {
            return new TreeSet<>() {
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

        public static record Route(Flight flight1, Flight flight2, Flight flight3, int sum, int speed, long id12, int id3) implements Comparable<Route> {
            public static Route from(List<Flight> flights, int sum) {
                if (flights.isEmpty() || flights.size() > 3) {
                    throw new IllegalArgumentException("Route can't be empty or have more than 3 flights.");
                }
                var speed = flights.getLast().arr() - flights.getFirst().dep();
                Flight flight1 = null, flight2 = null, flight3 = null;
                long id1 = 0, id2 = 0; int id3 = 0;
                try {
                    flight1 = flights.get(0); id1 = flight1.id;
                    flight2 = flights.get(1); id2 = flight2.id;
                    flight3 = flights.get(2); id3 = flight3.id;
                } catch (IndexOutOfBoundsException e) { }
                long id12 = (id1 << 32) | id2;
                return new Route(flight1, flight2, flight3, sum, speed, id12, id3);
            }

            public Flight[] getFlights() {
                if (flight2 == null) {
                    return new Flight[] {flight1};
                } else if (flight3 == null) {
                    return new Flight[] {flight1, flight2};
                } else {
                    return new Flight[] {flight1, flight2, flight3};
                }
            }

            @Override
            public int compareTo(Route another) {
                if (this.sum != another.sum) {
                    return this.sum - another.sum;
                }
                if (this.speed != another.speed) {
                    return this.speed - another.speed;
                }
                if (this.id12 != another.id12) {
                    return Long.compare(this.id12, another.id12);
                }
                return this.id3 - another.id3;
            }

            public int compareTo(List<Flight> flights, int sum) {
                if (this.sum != sum) {
                    return this.sum - sum;
                }

                var speed = flights.getLast().arr() - flights.getFirst().dep();
                if (this.speed != speed) {
                    return this.speed - speed;
                }

                long id1 = 0, id2 = 0; int id3 = 0;
                try {
                    id1 = flights.get(0).id;
                    id2 = flights.get(1).id;
                    id3 = flights.get(2).id;
                } catch (IndexOutOfBoundsException e) { }
                long id12 = (id1 << 32) | id2;
                if (this.id12 != id12) {
                    return Long.compare(this.id12, id12);
                }
                return this.id3 - id3;
            }
        }
    }
}
