package searchengine.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "site")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private Status status;

    @NonNull
    @Column(name = "status_time", updatable = true, columnDefinition = "DATETIME NOT NULL", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime statusTime = LocalDateTime.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @NonNull
    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @NonNull
    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    @OneToMany(mappedBy = "siteEntity", fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PageEntity> pages  = new ArrayList<>();

    @PreUpdate
    void update() {
        statusTime = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        statusTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "SitePage{" +
                "id=" + id +
                ", status=" + status +
                ", statusTime=" + statusTime +
                ", lastError='" + lastError + '\'' +
                ", url='" + url + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

}