import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import java.util.*;
import java.util.concurrent.*;

import java.util.*;
import java.util.concurrent.*;

import java.util.*;
import java.util.concurrent.*;

class DungeonQueue {
    private final int maxInstances;
    private final Semaphore instanceSlots;
    private final boolean[] instanceStatus;
    private final Object lock = new Object();

    private long servedParties = 0;
    private long startTime;
    private long endTime;

    private final Queue<String> logMessages = new ConcurrentLinkedQueue<>();

    public DungeonQueue(int maxInstances) {
        this.maxInstances = maxInstances;
        this.instanceSlots = new Semaphore(maxInstances);
        this.instanceStatus = new boolean[maxInstances];
        this.startTime = System.currentTimeMillis(); // Start timer at creation
        printStatus();
    }

    public void enterDungeon(long t1, long t2) {
        int instanceIndex = -1;
        try {
            instanceSlots.acquire();
            instanceIndex = acquireInstance();
            long dungeonTime = ThreadLocalRandom.current().nextLong(t1, t2 + 1);

            addLog("-> Party entered Dungeon Instance " + instanceIndex + ". Estimated time: " + dungeonTime + " sec.");
            printStatus();

            Thread.sleep(dungeonTime * 1000);

            synchronized (lock) {
                servedParties++;
                instanceStatus[instanceIndex] = false;
                addLog("<- Party cleared Dungeon Instance " + instanceIndex + " in " + dungeonTime + " sec.");
                printStatus();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (instanceIndex != -1) {
                instanceSlots.release();
            }
        }
    }

    private int acquireInstance() {
        synchronized (lock) {
            for (int i = 0; i < instanceStatus.length; i++) {
                if (!instanceStatus[i]) {
                    instanceStatus[i] = true;
                    return i;
                }
            }
        }
        throw new IllegalStateException("No available instance found after acquiring semaphore.");
    }

    private void printStatus() {
        synchronized (lock) {
            clearConsole();
            System.out.println("======== Dungeon Status ========");
            int columns = 4;
            for (int i = 0; i < instanceStatus.length; i++) {
                String status = instanceStatus[i] ? "[Active]" : "[Empty]";
                System.out.printf("Instance %2d: %-10s", i, status);
                if ((i + 1) % columns == 0 || i == instanceStatus.length - 1) {
                    System.out.println();
                }
            }
            System.out.println("================================");
            printLogMessages();
        }
    }

    private void printLogMessages() {
        System.out.println("======== Recent Updates ========");
        for (String message : logMessages) {
            System.out.println(message);
        }
        System.out.println("================================");
    }

    private void addLog(String message) {
        if (logMessages.size() >= 15) {
            logMessages.poll();
        }
        logMessages.offer(message);
    }

    private void clearConsole() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("Failed to clear console.");
        }
    }

    public void printSummary() {
        endTime = System.currentTimeMillis(); // Capture end time
        long totalElapsedTime = (endTime - startTime) / 1000; // Convert to seconds

        synchronized (lock) {
            System.out.println("================ Dungeon Summary ================");
            System.out.println("Total parties served: " + servedParties);
            System.out.println("Total time spent: " + totalElapsedTime + " sec.");
            System.out.println("================================================");
        }
    }
}




class PlayerQueue {
    private long tanks;
    private long healers;
    private long dps;
    private final DungeonQueue dungeonQueue;
    private final List<Thread> partyThreads = new ArrayList<>();

    private long initialTanks, initialHealers, initialDps; // To track initial values

    public PlayerQueue(long tanks, long healers, long dps, DungeonQueue dungeonQueue) {
        this.tanks = tanks;
        this.healers = healers;
        this.dps = dps;
        this.dungeonQueue = dungeonQueue;
        this.initialTanks = tanks;
        this.initialHealers = healers;
        this.initialDps = dps;
    }

    public void processQueue(long t1, long t2) {
        while (tanks > 0 && healers > 0 && dps >= 3) {
            tanks--;
            healers--;
            dps -= 3;
            Thread partyThread = new Thread(() -> dungeonQueue.enterDungeon(t1, t2));
            partyThreads.add(partyThread);
            partyThread.start();
        }
    }

    public void waitForCompletion() {
        for (Thread thread : partyThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void printLeftoverPlayers() {
        System.out.println("============= Leftover Players =============");
        System.out.println("Initial Tanks: " + initialTanks + " | Remaining: " + tanks);
        System.out.println("Initial Healers: " + initialHealers + " | Remaining: " + healers);
        System.out.println("Initial DPS: " + initialDps + " | Remaining: " + dps);
        System.out.println("===========================================");
    }
}

public class DungeonSimulator {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int maxInstances = getValidIntInput(scanner, "Enter maximum number of concurrent instances (≥1): ", 1, Integer.MAX_VALUE);
        long tanks = getValidLongInput(scanner, "Enter number of tank players in the queue (≥0): ", 0, Long.MAX_VALUE);
        long healers = getValidLongInput(scanner, "Enter number of healer players in the queue (≥0): ", 0, Long.MAX_VALUE);
        long dps = getValidLongInput(scanner, "Enter number of DPS players in the queue (≥0): ", 0, Long.MAX_VALUE);
        int t1 = getValidIntInput(scanner, "Enter minimum time before an instance is finished (1-15 sec): ", 1, 15);
        int t2 = getValidIntInput(scanner, "Enter maximum time before an instance is finished (" + t1 + "-15 sec): ", t1, 15);

        DungeonQueue dungeonQueue = new DungeonQueue(maxInstances);
        PlayerQueue playerQueue = new PlayerQueue(tanks, healers, dps, dungeonQueue);

        playerQueue.processQueue(t1, t2);
        playerQueue.waitForCompletion();

        dungeonQueue.printSummary();
        playerQueue.printLeftoverPlayers(); // Display remaining players
    }

    private static long getValidLongInput(Scanner scanner, String prompt, long min, long max) {
        long value;
        while (true) {
            System.out.print(prompt);
            if (scanner.hasNextLong()) {
                value = scanner.nextLong();
                if (value >= min && value <= max) {
                    return value;
                }
            } else {
                scanner.next(); // Consume invalid input
            }
            System.out.println("Invalid input. Please enter a number between " + min + " and " + max + ".");
        }
    }

    private static int getValidIntInput(Scanner scanner, String prompt, int min, int max) {
        int value;
        while (true) {
            System.out.print(prompt);
            if (scanner.hasNextInt()) {
                value = scanner.nextInt();
                if (value >= min && value <= max) {
                    return value;
                }
            } else {
                scanner.next(); // Consume invalid input
            }
            System.out.println("Invalid input. Please enter a number between " + min + " and " + max + ".");
        }
    }
}