package com.denizcan.demo.channel;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ChannelControllerTest {
    @Test void staleSnapshotCannotRestoreSoldStock() {
        ChannelController channel = new ChannelController("N11");
        channel.inventory(List.of(new ChannelController.Inventory("SKU", "Demo", BigDecimal.TEN, 4, 2)));
        channel.inventory(List.of(new ChannelController.Inventory("SKU", "Demo", BigDecimal.TEN, 8, 1)));
        assertThat(channel.inventory().getFirst().availableQuantity()).isEqualTo(4);
    }
    @Test void pendingLocalOrderIsDeductedUntilCentralAcknowledgement() {
        ChannelController channel = new ChannelController("TRENDYOL");
        channel.inventory(List.of(new ChannelController.Inventory("SKU", "Demo", BigDecimal.TEN, 5, 0)));
        channel.create(new ChannelController.NewOrder("EXT1", List.of(new ChannelController.Item("SKU", 2))));
        assertThat(channel.inventory().getFirst().availableQuantity()).isEqualTo(3);
        channel.acknowledge("EXT1", List.of(new ChannelController.Inventory("SKU", "Demo", BigDecimal.TEN, 3, 1)));
        assertThat(channel.inventory().getFirst().availableQuantity()).isEqualTo(3);
        assertThat(channel.pending()).isEmpty();
    }
}
