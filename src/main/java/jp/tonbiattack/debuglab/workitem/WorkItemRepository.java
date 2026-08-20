package jp.tonbiattack.debuglab.workitem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {

    @Query("select workItem from WorkItem workItem where workItem.assignee = :assignee")
    List<WorkItem> findByAssignee(@Param("assignee") String assignee);
}
