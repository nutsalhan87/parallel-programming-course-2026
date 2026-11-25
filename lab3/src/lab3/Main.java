package lab3;

import static lab3.Main.Top10.boundedTop10Set;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class Main {
    private static Schedule schedule;

    private static Map<Integer, SortedSet<Flight>> airportToFlights = new HashMap<>();

    private static Set<Flight> flightsAt(int airportId, int day) {
        var startDummy = new Flight(0, null, null, null, day * 1440, 0, 0);
        var endDummy = new Flight(0, null, null, null, (day + 1) * 1440, 0, 0);
        return airportToFlights.get(airportId).subSet(startDummy, endDummy);
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

        for (int flightId = 0; flightId < schedule.nFlights; ++flightId) {
            var flight = schedule.flight(flightId);
            var flights = airportToFlights.computeIfAbsent(
                schedule.airportId(flight.from()), 
                f -> new TreeSet<Flight>(Comparator.comparingInt(Flight::dep))
            );
            flights.add(flight);
        }

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
            reader.lines().map(query -> {
                var splitted = query.split("\s");
                return method.top10(splitted[0], splitted[1], Integer.parseUnsignedInt(splitted[2]));
            });
        } catch (IOException e) {
            System.err.println("Error opening queries file: " + e);
            System.exit(2);
        }
    }

    private static sealed interface Top10 permits Top10.Baseline, Top10.ForkJoin {
        SortedSet<List<Flight>> top10(String from, String to, int day);
        
        public static final class Baseline implements Top10 {
            private void top10Inner(List<Flight> route, int from, int to, int dep, int depth, SortedSet<List<Flight>> boundedTop10) {
                // var fromFlights = flightsAt(from, day);
                // Для первого рейса dep может быть от day*1440 до (day+1)*1440, но для остальных (depth > 0) работает правило, что пересадка от 1 до 6 часов
            } 

            public SortedSet<List<Flight>> top10(String from, String to, int day) {
                SortedSet<List<Flight>> boundedTop10 = boundedTop10Set();
                var fromId = schedule.airportId(from);
                var toId = schedule.airportId(to);
                top10Inner(new ArrayList<>(3), fromId, toId, day * 1440, 0, boundedTop10);
                return boundedTop10;
            }
        }

        public static final class ForkJoin implements Top10 {
            public SortedSet<List<Flight>> top10(String from, String to, int day) {
                return boundedTop10Set();
            }
        }

        private static SortedSet<List<Flight>> boundedTop10Set() {
            return new TreeSet<>((r1, r2) -> {
                var sum1 = r1.stream().mapToInt(Flight::basePrice).sum();
                var sum2 = r2.stream().mapToInt(Flight::basePrice).sum();
                if (sum1 != sum2) {
                    return sum1 - sum2;
                }

                var speed1 = r1.getLast().arr() - r1.getFirst().dep();
                var speed2 = r2.getLast().arr() - r2.getFirst().dep();
                if (speed1 != speed2) {
                    return speed1 - speed2;
                }

                for (int i = 0; i < Math.min(r1.size(), r2.size()); ++i) {
                    var id1 = r1.get(i).id();
                    var id2 = r2.get(i).id();
                    if (id1 != id2) {
                        return id1 - id2;
                    }
                }

                return r1.size() - r2.size();
            }) {
                @Override
                public boolean add(List<Flight> flights) {
                    super.add(flights);
                    if (this.size() > 10) {
                        var removed = this.removeLast();
                        if (removed == flights) {
                            return false;
                        }
                    }
                    return true;
                };
            };
        }
    }


}
