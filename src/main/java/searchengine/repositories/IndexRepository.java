package searchengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;

import java.util.List;
import java.util.Set;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Modifying
    @Query(value = "UPDATE lemma l SET l.frequency = l.frequency - 1 WHERE l.id " +
            "IN (SELECT i.lemma_id FROM search_index i WHERE i.page_id = :pageId)", nativeQuery = true)
    void decrementLemmaFrequencyByPage(@Param("pageId") Integer pageId);

    @Query(value = "SELECT page_id FROM search_index WHERE lemma_id = :lemmaId", nativeQuery = true)
    Set<Integer> findPageEntityIdsByLemmaEntityId(@Param("lemmaId") Integer lemmaId);

    @Query(value = "SELECT * FROM search_index i WHERE i.page_id = :pageId " +
            "AND i.lemma_id IN :lemmaIds", nativeQuery = true)
    Page<IndexEntity> findByPageIdAndLemmaIds(@Param("pageId") int pageId,
                                              @Param("lemmaIds") List<Integer> lemmaIds,
                                              Pageable pageable);
}
