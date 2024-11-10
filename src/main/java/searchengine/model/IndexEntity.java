package searchengine.model;

import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Entity
@Table(name = "search_index")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false,
            foreignKey=@ForeignKey(name = "fk_search_index_page"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PageEntity pageEntity;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false,
            foreignKey=@ForeignKey(name = "fk_search_index_lemma"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LemmaEntity lemmaEntity;

    @NonNull
    @Column(name = "`rank`", nullable = false)
    private float rank;
}
