package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lemma l WHERE l.frequency = 0", nativeQuery = true)
    void deleteUnusedLemmas();

    Optional<LemmaEntity> findByLemmaAndSiteEntity(String lemmaText, SiteEntity siteEntity);

    int countBySiteEntity(SiteEntity siteEntity);

    @Query("SELECT COUNT(l) FROM LemmaEntity l WHERE l.lemma = :lemma AND l.siteEntity IN :sites")
    int countByLemmaAndSites(@Param("lemma") String lemma, @Param("sites") List<SiteEntity> sites);

    @Query(value = "SELECT l.* FROM lemma l WHERE l.lemma IN :lemmas", nativeQuery = true)
    List<LemmaEntity> findLemmaEntityListByLemmasOnly(@Param("lemmas") List<String> lemmas);

    @Query(value = "SELECT l.* FROM lemma l WHERE l.lemma IN :lemmas AND l.site_id IN :siteIds", nativeQuery = true)
    List<LemmaEntity> findLemmaEntityListByLemmasAndSites(@Param("lemmas") List<String> lemmas, @Param("siteIds") List<Integer> siteIds);
}

