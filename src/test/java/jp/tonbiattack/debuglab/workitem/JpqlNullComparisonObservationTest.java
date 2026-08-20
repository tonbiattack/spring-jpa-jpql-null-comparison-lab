package jp.tonbiattack.debuglab.workitem;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class JpqlNullComparisonObservationTest {

    @Autowired
    private WorkItemRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void equalityComparisonWithNullReturnsNoRows_butIsNullFindsUnassignedRow() {
        WorkItem unassigned = repository.saveAndFlush(new WorkItem("draft-release", null));
        Long unassignedId = unassigned.getId();
        entityManager.clear();

        List<WorkItem> equalityResult = entityManager.createQuery(
                        "select workItem from WorkItem workItem where workItem.assignee = :assignee",
                        WorkItem.class)
                .setParameter("assignee", null)
                .getResultList();
        List<WorkItem> isNullResult = entityManager.createQuery(
                        "select workItem from WorkItem workItem where workItem.assignee is null",
                        WorkItem.class)
                .getResultList();

        assertAll(
                () -> assertEquals(List.of(), equalityResult.stream().map(WorkItem::getId).toList(),
                        "nullを等価比較すると未割当行は選択されない"),
                () -> assertEquals(List.of(unassignedId), isNullResult.stream().map(WorkItem::getId).toList(),
                        "IS NULL述語は未割当行を選択する")
        );
    }
}
