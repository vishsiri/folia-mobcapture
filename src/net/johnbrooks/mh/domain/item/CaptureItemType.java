package net.johnbrooks.mh.domain.item;

import lombok.Value;
import org.bukkit.Material;

import java.util.List;

@Value
public class CaptureItemType {
    String id;
    Material material;
    String displayName;
    List<String> lore;
    double chance;
}
