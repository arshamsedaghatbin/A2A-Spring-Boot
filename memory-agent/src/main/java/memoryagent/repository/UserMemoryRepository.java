package memoryagent.repository;

import memoryagent.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, String> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_memory
            (user_id, default_from_account, last_transfer_to, last_transaction_id,
             last_transaction_date, total_transactions, created_at, updated_at)
        VALUES
            (:userId, :fromAccount, :toAccount, :transactionId,
             NOW(), 1, NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET
            default_from_account  = EXCLUDED.default_from_account,
            last_transfer_to      = EXCLUDED.last_transfer_to,
            last_transaction_id   = EXCLUDED.last_transaction_id,
            last_transaction_date = NOW(),
            total_transactions    = user_memory.total_transactions + 1,
            updated_at            = NOW()
        """, nativeQuery = true)
    void upsert(
            @Param("userId") String userId,
            @Param("fromAccount") String fromAccount,
            @Param("toAccount") String toAccount,
            @Param("transactionId") String transactionId);
}
