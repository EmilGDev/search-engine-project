package searchengine.model;

import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Entity
@Table(name = "lemma")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lemma_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteEntity siteEntity;

    @NonNull
    @Column(columnDefinition = "varchar(255)", nullable = false)
    private String lemma;

    @NonNull
    @Column(nullable = false)
    private int frequency;

}
