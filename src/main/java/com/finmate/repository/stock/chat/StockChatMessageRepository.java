package com.finmate.repository.stock.chat;

import com.finmate.domain.stock.chat.StockChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockChatMessageRepository extends JpaRepository<StockChatMessage, Long> {

    @Query("""
            select message
            from StockChatMessage message
            join fetch message.stock
            join fetch message.user
            left join fetch message.parentMessage parentMessage
            left join fetch parentMessage.user
            where message.stock.id = :stockId
              and (:beforeId is null or message.id < :beforeId)
            order by message.id desc
            """)
    List<StockChatMessage> findHistory(
            @Param("stockId") Long stockId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("""
            select max(message.id)
            from StockChatMessage message
            where message.stock.id = :stockId
            """)
    Optional<Long> findLatestMessageId(@Param("stockId") Long stockId);

    @Query("""
            select message
            from StockChatMessage message
            join fetch message.stock
            join fetch message.user
            left join fetch message.parentMessage parentMessage
            left join fetch parentMessage.user
            where message.id = :messageId
            """)
    Optional<StockChatMessage> findByIdWithRelations(@Param("messageId") Long messageId);
}
