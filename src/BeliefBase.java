import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BeliefBase{
    private final Map<Integer, List<Belief>> byThread = new HashMap<>();

    public void add(Belief b) {
        int threadID = b.getThreadID();
        if (!contains(threadID)) {
            byThread.put(threadID, new ArrayList<>());   
        }
        byThread.get(threadID).add(b);
    }

    public List<Belief> get(int threadID){
        List<Belief> information_required = new ArrayList<>();
        information_required.addAll(byThread.getOrDefault(Belief.NO_THREAD, List.of())); // si la cle existe, retourn la, sinon retourne une liste vide via List.of()
        information_required.addAll(byThread.getOrDefault(threadID, List.of()));

        return information_required;
    }

    public boolean contains(int threadID){
        List<Belief> list = byThread.get(threadID);
        if (list == null){
            return false;
        }
        return true;
    }

    public void loadContext(List<String> contextFiles) throws IOException {
        /*
        Load basic beliefs 
         */
        byThread.remove(Belief.NO_THREAD);

        for (String contextFile : contextFiles) {
            for (String line : Files.readAllLines(Path.of(contextFile))) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;
                String date    = parts[0].trim();
                String content = parts[1].trim();
                add(new Belief(content, 1.0, Belief.Source.BASE, Belief.NO_THREAD, date));
            }
        }
    }

    public void saveMemory(String file) throws IOException {
        /*
        Save beliefs learnt afterwards (via mail) in a file 
         */
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, List<Belief>> entry : byThread.entrySet()) {
            if (entry.getKey() == Belief.NO_THREAD) continue; 
            for (Belief b : entry.getValue()) {
                lines.add(b.getThreadID() + "|" + b.getSource() + "|" + b.getConfidence()
                        + "|" + b.getDate() + "|" + oneLine(b.getContent()));
            }
        }
        Files.write(Path.of(file), lines);
    }

    public void loadMemory(String file) throws IOException {
        /*
        Load beliefs learnt afterwards (via mail)
         */
        Path path = Path.of(file);
        if (!Files.exists(path)) return;
        for (String line : Files.readAllLines(path)) {
            String[] p = line.split("\\|", 5);
            if (p.length == 5){
                add(new Belief(p[4], Double.parseDouble(p[2]),
                        Belief.Source.valueOf(p[1]), Integer.parseInt(p[0]), p[3]));
            }

        }
    }

    private static String oneLine(String s) {
        return s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }
}