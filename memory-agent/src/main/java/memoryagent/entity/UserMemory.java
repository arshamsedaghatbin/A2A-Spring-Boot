package memoryagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "default_from_account")
    private String defaultFromAccount;

    @Column(name = "last_transfer_to")
    private String lastTransferTo;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column(name = "last_transaction_id")
    private String lastTransactionId;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "total_transactions")
    private Integer totalTransactions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserMemory() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDefaultFromAccount() { return defaultFromAccount; }
    public void setDefaultFromAccount(String defaultFromAccount) { this.defaultFromAccount = defaultFromAccount; }

    public String getLastTransferTo() { return lastTransferTo; }
    public void setLastTransferTo(String lastTransferTo) { this.lastTransferTo = lastTransferTo; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getLastTransactionId() { return lastTransactionId; }
    public void setLastTransactionId(String lastTransactionId) { this.lastTransactionId = lastTransactionId; }

    public LocalDateTime getLastTransactionDate() { return lastTransactionDate; }
    public void setLastTransactionDate(LocalDateTime lastTransactionDate) { this.lastTransactionDate = lastTransactionDate; }

    public Integer getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Integer totalTransactions) { this.totalTransactions = totalTransactions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
