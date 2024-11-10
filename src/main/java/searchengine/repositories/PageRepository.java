package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    boolean existsByPath(String path);
    Optional<PageEntity> findByPathAndSiteEntity(String url, SiteEntity siteEntity);
    int countBySiteEntity(SiteEntity siteEntity);
}

