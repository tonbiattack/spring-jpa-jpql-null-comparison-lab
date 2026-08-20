package jp.tonbiattack.debuglab.workitem;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class WorkItemRepositoryTest {

    @Autowired
    private WorkItemRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByAssignee_withNull_returnsPersistedUnassignedWorkItem() {
        WorkItem unassigned = repository.saveAndFlush(new WorkItem("draft-release", null));
        repository.saveAndFlush(new WorkItem("review-release", "mika"));
        Long unassignedId = unassigned.getId();
        entityManager.clear();

        List<WorkItem> found = repository.findByAssignee(null);

        entityManager.clear();
        WorkItem persistedUnassigned = repository.findById(unassignedId).orElseThrow();

        assertAll(
                () -> assertNull(persistedUnassigned.getAssignee(),
                        "DBには未割当WorkItemが保存されている"),
                () -> assertEquals(1, found.size(),
                        "未割当の検索結果は一件である"),
                () -> assertEquals(List.of(unassignedId), found.stream().map(WorkItem::getId).toList(),
                        "検索結果は保存済みの未割当WorkItemを返す")
        );
    }
}
