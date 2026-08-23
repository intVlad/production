package com.example.productionmvp.repository;

import com.example.productionmvp.model.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.Worker;

public interface TimeLogRepository extends JpaRepository<TimeLog, UUID> {
    Optional<TimeLog> findByTaskAndWorkerAndEndTimeIsNull(Task task, Worker worker);
    
    // For blocking tasks, we just want to close whatever session is open, regardless of who blocks it
    Optional<TimeLog> findByTaskAndEndTimeIsNull(Task task);

    // Defensive variant: a race between two concurrent starts on the same task can leave more
    // than one open TimeLog behind (see TaskRepository.findByIdLocked), which would make the
    // Optional-returning query above throw IncorrectResultSizeDataAccessException instead of
    // closing them out. Used to recover/close all open logs rather than crash.
    java.util.List<TimeLog> findAllByTaskAndEndTimeIsNull(Task task);
    
    // Efficiently sum all duration seconds for the dashboard
    @org.springframework.data.jpa.repository.Query("SELECT SUM(t.durationSeconds) FROM TimeLog t")
    Long sumAllDurations();
    
    // For recent history
    java.util.List<TimeLog> findTop10ByOrderByStartTimeDesc();
    
    @org.springframework.transaction.annotation.Transactional
    void deleteByTask(Task task);
    
    java.util.List<TimeLog> findByTask(Task task);
}
