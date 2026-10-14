package net.johnbrooks.mh.commands;

import net.johnbrooks.mh.domain.item.CaptureItemType;
import net.johnbrooks.mh.domain.item.UniqueProjectileData;
import net.johnbrooks.mh.i18n.Message;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.node.ExecutionContext;
import revxrsal.commands.parameter.ParameterType;
import revxrsal.commands.stream.MutableStringStream;

import java.util.Collection;
import java.util.stream.Collectors;

public class CaptureItemTypeResolver implements ParameterType<BukkitCommandActor, CaptureItemType> {

    @Override
    public CaptureItemType parse(@NotNull MutableStringStream input, @NotNull ExecutionContext<BukkitCommandActor> context) {
        String typeId = input.readString();
        return UniqueProjectileData.getType(typeId).orElseThrow(() -> {
            String availableTypes = UniqueProjectileData.getTypes().stream()
                    .map(CaptureItemType::getId)
                    .collect(Collectors.joining(", "));
            return new InvalidCaptureTypeException(typeId, availableTypes);
        });
    }

    @Override
    public @NotNull SuggestionProvider<BukkitCommandActor> defaultSuggestions() {
        return (context) -> UniqueProjectileData.getTypes().stream()
                .map(CaptureItemType::getId)
                .collect(Collectors.toList());
    }
}
