package memoryagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;

/**
 * Maps to conversation_memory table.
 * The embedding column (vector(768)) is NOT mapped via JPA because pgvector
 * requires special JDBC handling. All vector read/write operations go through
 * JdbcTemplate in MemoryService using PGobject with type="vector".
 */
@Entity
@Table(name = "conversation_memory")
public class ConversationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Held in-memory only — persisted via JdbcTemplate with PGobject(type=vector). */
    @Transient
    @Column(columnDefinition = "vector(768)")
    private float[] embedding;

    @Column(name = "memory_type")
    private String memoryType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ConversationMemory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
