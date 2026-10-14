package net.johnbrooks.mh.commands;

import net.johnbrooks.mh.i18n.Message;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.exception.BukkitExceptionHandler;
import revxrsal.commands.bukkit.exception.SenderNotPlayerException;
import revxrsal.commands.exception.InvalidIntegerException;
import revxrsal.commands.exception.MissingArgumentException;
import revxrsal.commands.exception.NoPermissionException;
import revxrsal.commands.exception.RuntimeExceptionAdapter;
import revxrsal.commands.exception.UnknownCommandException;

public final class MobCaptureCommandExceptionHandler extends BukkitExceptionHandler {
    @Override
    public void onNoPermission(NoPermissionException exception, BukkitCommandActor actor) {
        Message.sendKey(actor.sender(), "no-permission-command");
    }

    @Override
    public void onSenderNotPlayer(SenderNotPlayerException exception, BukkitCommandActor actor) {
        Message.sendKey(actor.sender(), "player-only");
    }

    @Override
    public void onUnknownCommand(UnknownCommandException exception, BukkitCommandActor actor) {
        Message.sendKey(actor.sender(), "unknown-command");
    }

    @Override
    public void onMissingArgument(MissingArgumentException exception, BukkitCommandActor actor, revxrsal.commands.node.ParameterNode<BukkitCommandActor, ?> parameter) {
        Message.sendKey(actor.sender(), "unknown-command");
    }

    @Override
    public void onInvalidInteger(InvalidIntegerException exception, BukkitCommandActor actor) {
        Message.sendKey(actor.sender(), "invalid-number");
    }

    @RuntimeExceptionAdapter.HandleException
    public void onInvalidCaptureType(InvalidCaptureTypeException exception, BukkitCommandActor actor) {
        Message.sendKey(actor.sender(), "unknown-capture-type", "types", exception.getAvailableTypes());
    }
}
