package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM site WHERE url = :url", nativeQuery = true)
    void deleteSiteByUrl(@Param("url") String url);

    Optional<SiteEntity> findByUrl(String url);

    Optional<SiteEntity> findByUrlAndStatus(String url, Status status);

    List<SiteEntity> findAllByStatus(Status status);

    boolean existsByStatus(Status status);

}
