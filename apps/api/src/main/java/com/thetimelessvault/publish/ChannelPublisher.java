package com.thetimelessvault.publish;

import com.thetimelessvault.common.Platform;
import com.thetimelessvault.inventory.InventoryItem;

import java.util.List;

public interface ChannelPublisher {
    Platform platform();

    PublishResult publish(InventoryItem item, List<String> photoUrls);
}
