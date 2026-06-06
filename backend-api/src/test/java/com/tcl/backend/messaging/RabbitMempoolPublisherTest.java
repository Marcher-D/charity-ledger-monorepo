package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMempoolPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final TransactionSigner transactionSigner = mock(TransactionSigner.class);
    private final RabbitMempoolPublisher publisher = new RabbitMempoolPublisher(
            rabbitTemplate,
            new RabbitQueueProperties("mempool_exchange", "mempool_queue", "mempool_dlq", "event_bus_queue", "event_bus_dlq"),
            transactionSigner,
            fixedNonceService(),
            new MoneyFormatter()
    );

    @Test
    void publishSignsTransactionStoresHashAndSendsMessage() {
        TransactionCache transaction = new TransactionCache();
        transaction.setId(UUID.randomUUID());
        transaction.setType(TransactionType.STAKE);
        transaction.setAmount(new BigDecimal("10"));
        transaction.setNote("STAKE");

        when(transactionSigner.sign(
                eq(transaction),
                eq(123L),
                eq("0xsender"),
                eq("STAKE_POOL"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyMap()
        )).thenReturn(new SignedTransaction("public-key", "signature", "tx-hash"));

        publisher.publish(transaction, "0xsender", "STAKE_POOL");

        assertThat(transaction.getTxHash()).isEqualTo("tx-hash");

        ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transactionSigner).sign(
                eq(transaction),
                eq(123L),
                eq("0xsender"),
                eq("STAKE_POOL"),
                timestampCaptor.capture(),
                payloadCaptor.capture()
        );

        ArgumentCaptor<OutboundTransactionMessage> messageCaptor = ArgumentCaptor.forClass(OutboundTransactionMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("mempool_exchange"), eq("mempool_queue"), messageCaptor.capture());

        OutboundTransactionMessage message = messageCaptor.getValue();
        assertThat(message.txId()).isEqualTo(transaction.getId());
        assertThat(message.nonceOrder()).isEqualTo(123L);
        assertThat(message.type()).isEqualTo(TransactionType.STAKE);
        assertThat(message.senderAddress()).isEqualTo("0xsender");
        assertThat(message.targetAddress()).isEqualTo("STAKE_POOL");
        assertThat(message.amount()).isEqualTo("10.00");
        assertThat(message.timestamp()).isEqualTo(timestampCaptor.getValue());
        assertThat(message.payload()).isEqualTo(payloadCaptor.getValue());
        assertThat(message.publicKey()).isEqualTo("public-key");
        assertThat(message.signature()).isEqualTo("signature");
        assertThat(message.txHash()).isEqualTo("tx-hash");
    }

    private NonceService fixedNonceService() {
        return new NonceService() {
            @Override
            public long nextNonceOrder() {
                return 123L;
            }
        };
    }
}
