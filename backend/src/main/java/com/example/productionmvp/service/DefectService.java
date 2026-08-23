package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.Assembly;
import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.AssemblyInstanceStatus;
import com.example.productionmvp.model.Batch;
import com.example.productionmvp.model.BatchStatus;
import com.example.productionmvp.model.DefectRecord;
import com.example.productionmvp.model.DefectResolution;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.model.OperationType;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.AssemblyInstanceRepository;
import com.example.productionmvp.repository.BatchRepository;
import com.example.productionmvp.repository.DefectRecordRepository;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefectService {

    private final DefectRecordRepository defectRecordRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final BatchRepository batchRepository;

    public DefectService(DefectRecordRepository defectRecordRepository,
                         AssemblyInstanceRepository assemblyInstanceRepository,
                         TaskRepository taskRepository,
                         WorkerRepository workerRepository,
                         BatchRepository batchRepository) {
        this.defectRecordRepository = defectRecordRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public DefectRecord reportDefect(UUID assemblyInstanceId, UUID taskId, String reason, UUID confirmedById, DefectResolution resolution) {
        AssemblyInstance instance = null;
        if (assemblyInstanceId != null) {
            instance = assemblyInstanceRepository.findById(assemblyInstanceId)
                    .orElseThrow(() -> new EntityNotFoundException("AssemblyInstance not found: " + assemblyInstanceId));
        }

        Task task = null;
        if (taskId != null) {
            task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        }

        Worker worker = null;
        if (confirmedById != null) {
            worker = workerRepository.findById(confirmedById)
                    .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + confirmedById));
        }

        if (instance != null) {
            instance.setStatus(AssemblyInstanceStatus.DAMAGED);
            assemblyInstanceRepository.save(instance);
        }

        DefectRecord defectRecord = new DefectRecord();
        defectRecord.setAssemblyInstance(instance);
        defectRecord.setTask(task);
        defectRecord.setReason(reason);
        defectRecord.setConfirmedBy(worker);
        defectRecord.setResolution(resolution);
        defectRecord.setCreatedAt(LocalDateTime.now());

        if (resolution == DefectResolution.REWORK && task != null && instance != null) {
            Task reworkTask = new Task();
            reworkTask.setAssemblyInstance(instance);
            reworkTask.setOperation(task.getOperation());
            reworkTask.setSeries(task.getSeries());
            reworkTask.setStatus(TaskStatus.READY);
            reworkTask.setTaskType(OperationType.INDIVIDUAL);
            reworkTask.setCreatedAt(LocalDateTime.now());
            // Without a post, this task is permanently invisible to every worker's queue
            // (findAvailableTasksForPost joins on t.post.id, which never matches NULL).
            reworkTask.setPost(task.getOperation() != null ? task.getOperation().getPost() : null);
            taskRepository.save(reworkTask);
        } else if (resolution == DefectResolution.REPLACE && task != null) {
            Batch batch = new Batch();
            batch.setNumber("RPL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            batch.setPlannedQuantity(1);
            batch.setStatus(BatchStatus.CREATED);
            batch.setOperation(task.getOperation());
            if (task.getOperation() != null) {
                batch.setSection(task.getOperation().getSection());
                batch.setPost(task.getOperation().getPost());
            }
            batchRepository.save(batch);

            Task replaceTask = new Task();
            replaceTask.setBatch(batch);
            replaceTask.setSeries(task.getSeries());
            replaceTask.setOperation(task.getOperation());
            replaceTask.setStatus(TaskStatus.READY);
            replaceTask.setTaskType(OperationType.BATCH);
            replaceTask.setCreatedAt(LocalDateTime.now());
            replaceTask.setPost(batch.getPost());
            taskRepository.save(replaceTask);

            defectRecord.setReplacementBatch(batch);
        } else if (resolution == DefectResolution.WRITE_OFF && instance != null) {
            // "Списати вузол цілком": scrap the whole assembly instance and
            // auto-generate a batch task to rebuild all of its parts from scratch,
            // starting from the assembly's first operation. The rest of the assembly's
            // operations unlock one by one as each step completes, same as normal production.
            Assembly assembly = instance.getAssembly();
            Operation firstOp = null;
            if (assembly != null && assembly.getOperations() != null) {
                firstOp = assembly.getOperations().stream()
                        .filter(op -> op.getDependsOnOperation() == null)
                        .findFirst()
                        .orElse(null);
            }
            if (firstOp == null && task != null) {
                firstOp = task.getOperation();
            }

            Batch batch = new Batch();
            batch.setNumber("WOF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            batch.setPlannedQuantity(1);
            batch.setStatus(BatchStatus.CREATED);
            batch.setOperation(firstOp);
            if (firstOp != null) {
                batch.setSection(firstOp.getSection());
                batch.setPost(firstOp.getPost());
            }
            batchRepository.save(batch);

            Task rebuildTask = new Task();
            rebuildTask.setBatch(batch);
            rebuildTask.setAssemblyInstance(instance);
            rebuildTask.setSeries(task != null ? task.getSeries() : null);
            rebuildTask.setOperation(firstOp);
            rebuildTask.setStatus(TaskStatus.READY);
            rebuildTask.setTaskType(OperationType.BATCH);
            rebuildTask.setCreatedAt(LocalDateTime.now());
            rebuildTask.setPost(batch.getPost());
            taskRepository.save(rebuildTask);

            instance.setCurrentOperationIndex(0);
            assemblyInstanceRepository.save(instance);

            defectRecord.setReplacementBatch(batch);
        }

        return defectRecordRepository.save(defectRecord);
    }

    public List<DefectRecord> findDefectsSince(LocalDateTime since) {
        return defectRecordRepository.findAll().stream()
                .filter(d -> d.getCreatedAt().isAfter(since))
                .toList();
    }

    public Map<DefectResolution, Long> getDefectStats() {
        return defectRecordRepository.findAll().stream()
                .collect(Collectors.groupingBy(DefectRecord::getResolution, Collectors.counting()));
    }
}
