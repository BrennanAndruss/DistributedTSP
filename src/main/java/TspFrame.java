import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * A simple Swing GUI for loading a TSPLIB .tsp file,
 * displaying the cities, and computing a tour using the nearest neighbor heuristic.
 *
 * @author javiergs
 * @version 1.0
 */
public class TspFrame extends JFrame {

    // Set to test distributed system on a single local machine with only local workers
    private final boolean testingLocalOnly = true;

    // Set to test distributed system on a single local machine with "remote" workers
    private final boolean testingRemoteLocally = false;

    private final MapPanel mapPanel = new MapPanel();
    private final JTextArea log = new JTextArea(8, 60);
    private List<City> cities = List.of();
    private List<Integer> tour = List.of();

    private TspBlackboard blackboard = new TspBlackboard();

    public TspFrame() {
        super("Demo (TSPLIB + Nearest Neighbor)");
        log.setEditable(false);
        log.setBackground(new Color(200, 255, 220));
        JPanel top = getJPanel();
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(mapPanel, BorderLayout.CENTER);
        add(new JScrollPane(log), BorderLayout.SOUTH);
        log.append("Ready: Load a Waterloo TSPLIB file and draw cities.\n");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
    }

    private JPanel getJPanel() {
        JButton loadBtn = new JButton("Load .tsp");
        JButton solveBtn = new JButton("Nearest Neighbor");
        JButton clearBtn = new JButton("Clear Tour");
        JButton assistBtn = new JButton("Assist Remote");
        loadBtn.addActionListener(e -> onLoad());
        solveBtn.addActionListener(e -> onSolve());
        clearBtn.addActionListener(e -> onClear());
        assistBtn.addActionListener(e -> onAssist());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(loadBtn);
        top.add(solveBtn);
        top.add(clearBtn);
        top.add(assistBtn);
        return top;
    }

    private void onLoad() {
        String defaultUrl = "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/lu980.tsp";
        String urlString = JOptionPane.showInputDialog(this,
                "Enter TSP URL:",
                defaultUrl);

        if (urlString == null || urlString.trim().isEmpty()) return;

        try {
            cities = blackboard.getCities(urlString);
            blackboard.setCurrentMapUrl(urlString);

            tour = List.of();
            mapPanel.setCities(cities);
            log.append("\nLoaded from URL: " + urlString + "\n");
            log.append("Cities: " + cities.size() + "\n");
        } catch (Exception ex) {
            log.append("\nERROR: " + ex.getMessage() + "\n");
        }
    }

    private void onSolve() {
        if (cities == null || cities.size() < 2) {
            log.append("\nLoad a file first.\n");
            return;
        }

        System.out.println(blackboard.getCurrentMapUrl());

        // Configure threads
        int cores = Runtime.getRuntime().availableProcessors() / 2;
        if (testingRemoteLocally) {
            // Allocate half of the machine's cores to local threads
            cores /= 2;
        }

        Thread producer = new Thread(new Producer(blackboard));

        Thread outsourcer;
        if (!testingLocalOnly) {
            outsourcer = new Thread(new Outsourcer(blackboard));
        }

        int workerCores = cores - 2; // 2 for producer and main thread
        if (!testingLocalOnly) {
            // 1 for outsourcer, 1 more to prevent thread explosion from degrading MQTT connections
            workerCores -= 2;
        }

        Thread[] localWorkers = new Thread[workerCores];
        for (int i = 0; i < workerCores; i++) {
            localWorkers[i] = new Thread(new LocalWorker(i, blackboard));
        }

        // Start threads
        long startTime = System.currentTimeMillis();

        producer.start();
        if (!testingLocalOnly) {
            outsourcer.start();
        }
        for (Thread t : localWorkers) {
            t.start();
        }

        // Wait for completion
        try {
            producer.join();
            System.out.println("All jobs created.");
            for (int i = 0; i < cores - 1; i++) {
                blackboard.putJob(TspJob.STOP);
            }

            for (Thread t : localWorkers) {
                t.join();
            }
            if (!testingLocalOnly) {
                outsourcer.join();
            }
            System.out.println("All jobs finished.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        long endTime = System.currentTimeMillis() - startTime;

        tour = blackboard.getBestTour();
        double len = blackboard.getBestTourLength();

        // potential to-do: invoke in tspblackboard
        mapPanel.setTour(tour);

        log.append("\nNearest-neighbor tour computed.\n");
        log.append("Tour length (Euclidean): " + String.format("%.3f", len) + "\n");
        log.append("Total time: " + (endTime - startTime) + " ms");
    }

    private void onClear() {
        tour = List.of();
        mapPanel.setTour(tour);
        log.append("\nTour cleared.\n");
    }

    private void onAssist() {
        // Configure threads
        int cores = Runtime.getRuntime().availableProcessors() / 2;
        if (testingRemoteLocally) {
            // Allocate half of the machine's cores to local threads
            cores /= 2;
        }

        int workerCores = cores - 1; // 1 for main thread

        Thread[] remoteWorkers = new Thread[workerCores];
        for (int i = 0; i < workerCores; i++) {
            remoteWorkers[i] = new Thread(new RemoteWorker(i, blackboard));
            remoteWorkers[i].start();
            System.out.println("RemoteWorker " + i + " started.");
        }

        // Wait for completion
        try {
            for (Thread t : remoteWorkers) {
                t.join();
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        log.append("\nNearest-neighbor tour computed.\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TspFrame().setVisible(true));
    }
}