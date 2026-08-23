package com.example.productionmvp;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import com.example.productionmvp.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Properties that must hold after <em>every</em> operation, checked against randomised
 * sequences of real business calls rather than one scripted happy path.
 *
 * <p>The walkthrough-style tests elsewhere confirm that a known-good sequence produces a
 * known-good result. That cannot catch a rule broken only by an order of events nobody
 * thought to script — which is where the material-accounting and post-capacity bugs in this
 * codebase have historically lived. Here the sequence is generated, most steps are expected
 * to be rejected, and the assertion is that the rules survive regardless.
 *
 * <p>The seed is fixed so a failure is reproducible; print it and pass it back to re-run the
 * exact sequence.
 */
@SpringBootTest
class SystemInvariantsTest {

    @Autowired private MaterialRepository materialRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TimeLogRepository timeLogRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private ProductModelRepository productModelRepository;

    @Autowired private TaskExecutionService taskExecutionService;
    @Autowired private SeriesService seriesService;
    @Autowired private WorkerRepository workerRepository;

    /** available + reserved + used never changes: those three buckets only pass value around. */
    private double totalAccountedStock() {
        double total = 0;
        for (Material m : materialRepository.findAll()) {
            total += nz(m.getAvailableStock()) + nz(m.getReservedQuantity()) + nz(m.getUsedQuantity());
        }
        return total;
    }

    private static double nz(Double d) { return d == null ? 0.0 : d; }

    private void assertInvariants(String afterStep) {
        for (Material m : materialRepository.findAll()) {
            assertTrue(nz(m.getAvailableStock()) >= 0,
                    afterStep + ": " + m.getName() + " has negative free stock " + m.getAvailableStock());
            assertTrue(nz(m.getReservedQuantity()) >= 0,
                    afterStep + ": " + m.getName() + " has negative reserved " + m.getReservedQuantity());
            assertTrue(nz(m.getUsedQuantity()) >= 0,
                    afterStep + ": " + m.getName() + " has negative used " + m.getUsedQuantity());
        }

        for (Post p : postRepository.findAll()) {
            int load = p.getCurrentLoad() == null ? 0 : p.getCurrentLoad();
            int cap = p.getMaxCapacity() == null ? 0 : p.getMaxCapacity();
            assertTrue(load >= 0, afterStep + ": post " + p.getName() + " has negative load " + load);
            assertTrue(load <= cap,
                    afterStep + ": post " + p.getName() + " is over capacity " + load + "/" + cap);
        }

        for (Task t : taskRepository.findAll()) {
            List<TimeLog> open = timeLogRepository.findAllByTaskAndEndTimeIsNull(t);

            // Two open logs on one task is the signature of a lost start-race; it also makes
            // every later duration double-count.
            assertTrue(open.size() <= 1,
                    afterStep + ": task " + t.getId() + " has " + open.size() + " open time logs");

            if (t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED) {
                assertTrue(open.isEmpty(),
                        afterStep + ": " + t.getStatus() + " task " + t.getId() + " still has an open time log");
            }
            if (t.getStatus() == TaskStatus.IN_PROGRESS) {
                assertNotNull(t.getAssignedWorker(),
                        afterStep + ": task " + t.getId() + " is IN_PROGRESS with nobody assigned");
                assertNotNull(t.getStartedAt(),
                        afterStep + ": task " + t.getId() + " is IN_PROGRESS with no start time");
            }
            if (t.getActualTimeMinutes() != null) {
                assertTrue(t.getActualTimeMinutes() >= 0,
                        afterStep + ": task " + t.getId() + " has negative actual time");
            }
        }

        for (Series s : seriesRepository.findAll()) {
            var progress = seriesService.getSeriesProgress(s.getId());
            assertTrue(progress.getPercentage() >= 0 && progress.getPercentage() <= 100,
                    afterStep + ": series " + s.getNumber() + " progress out of range: " + progress.getPercentage());
            assertTrue(progress.getCompleted() <= progress.getTotalProducts(),
                    afterStep + ": series " + s.getNumber() + " reports more finished than planned");
        }

        for (Worker w : workerRepository.findAll()) {
            assertTrue(w.getTotalWorkedMinutes() == null || w.getTotalWorkedMinutes() >= 0,
                    afterStep + ": worker " + w.getName() + " has negative worked minutes");
        }
    }

    /**
     * An operation that is plausible for this task's current state. Cancel is deliberately rare:
     * cancelling tasks faster than they can be worked starves the run of the completed tasks
     * that exercise material consumption.
     */
    private static int legalMove(Task task, Random rnd) {
        return switch (task.getStatus()) {
            case READY, CREATED, ASSIGNED -> rnd.nextInt(12) == 0 ? 4 : 0;              // start (rarely cancel)
            case IN_PROGRESS -> switch (rnd.nextInt(6)) {
                case 0 -> 1;                                                            // pause
                case 1 -> 6;                                                            // damage
                default -> 3;                                                           // complete
            };
            case PAUSED -> 2;                                                           // resume
            case COMPLETED -> rnd.nextInt(4) == 0 ? 5 : 3;                               // occasionally reopen
            default -> rnd.nextInt(7);
        };
    }

    @Test
    @DisplayName("randomised operation sequences never break the system's accounting rules")
    void randomisedOperations_PreserveInvariants() {
        final long seed = 20260823L;
        Random rnd = new Random(seed);

        ProductModel model = productModelRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(model, "seeded data is required for this test");

        List<Worker> workers = workerRepository.findAll().stream()
                .filter(w -> w.getSystemRole() == SystemRole.WORKER)
                .toList();
        assertFalse(workers.isEmpty(), "seeded workers are required for this test");

        assertInvariants("baseline");
        double stockAtStart = totalAccountedStock();

        // Two series so tasks from different orders compete for the same posts and materials.
        for (int i = 0; i < 2; i++) {
            seriesService.createSeries(model.getId(), "INV-" + seed + "-" + i, 2 + rnd.nextInt(2),
                    SeriesPriority.MEDIUM, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
            assertInvariants("createSeries #" + i);
        }

        int rejected = 0;
        int[] succeeded = new int[7];
        for (int step = 0; step < 400; step++) {
            List<Task> tasks = taskRepository.findAll();
            if (tasks.isEmpty()) break;
            Task task = tasks.get(rnd.nextInt(tasks.size()));
            Worker worker = workers.get(rnd.nextInt(workers.size()));
            UUID id = task.getId();

            // A uniformly random operation is almost always illegal for whatever state the
            // task happens to be in, so the walk never reaches the interesting states: an
            // earlier version of this test completed zero tasks in 220 steps and therefore
            // never ran the material-consumption path at all. Bias towards a move that is
            // legal here, keeping a share of deliberately illegal ones to check that a
            // rejected operation leaves the system untouched.
            int op = rnd.nextInt(10) < 8 ? legalMove(task, rnd) : rnd.nextInt(7);

            try {
                switch (op) {
                    case 0 -> taskExecutionService.startTask(id, worker.getId());
                    case 1 -> taskExecutionService.pauseTask(id, worker.getId());
                    case 2 -> taskExecutionService.resumeTask(id, worker.getId());
                    case 3 -> taskExecutionService.completeTask(id, worker.getId());
                    case 4 -> taskExecutionService.cancelTask(id, worker.getId());
                    case 5 -> taskExecutionService.reopenTask(id, worker.getId());
                    case 6 -> taskExecutionService.markDamaged(id, worker.getId(), "інваріант-тест",
                            DefectResolution.REWORK);
                }
                succeeded[op]++;
            } catch (RuntimeException expected) {
                rejected++;
            }

            assertInvariants("step " + step);
        }

        // Nothing in this whole sequence creates or destroys material - reservation, consumption,
        // release and reversal only move quantity between the three buckets.
        assertEquals(stockAtStart, totalAccountedStock(), 0.0001,
                "material was created or destroyed across the run (seed " + seed + ")");

        assertTrue(rejected > 0, "the sequence never exercised a rejected operation");

        // Guards against the failure this test already had once: with a uniformly random
        // operation choice every step was rejected, nothing was ever completed, and the
        // material-consumption path went untested while the test still reported success.
        String[] names = {"start", "pause", "resume", "complete", "cancel", "reopen", "damage"};
        for (int i = 0; i < names.length; i++) {
            assertTrue(succeeded[i] > 0,
                    "no '" + names[i] + "' operation ever succeeded, so that path went unchecked");
        }
    }
}
