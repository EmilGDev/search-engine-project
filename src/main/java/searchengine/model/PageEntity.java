package searchengine.model;

import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Entity
@Table(
        name = "page",
        indexes = @Index(name = "idx_path", columnList = "path"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_page_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteEntity;

    @NonNull
    @Column(name = "path", columnDefinition = "TEXT", nullable = false)
    private String path;

    @NonNull
    @Column(name = "code", nullable = false)
    private int code;

    @NonNull
    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
