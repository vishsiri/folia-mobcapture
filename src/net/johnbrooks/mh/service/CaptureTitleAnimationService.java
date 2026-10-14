package net.johnbrooks.mh.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.i18n.Message;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class CaptureTitleAnimationService {
    private static final Duration NO_FADE = Duration.ZERO;
    private static final Duration RESULT_FADE_OUT = Duration.ofMillis(500);

    private final ConcurrentMap<UUID, Reservation> playerReservations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Reservation> entityReservations = new ConcurrentHashMap<>();
    private final AtomicLong nextAnimationId = new AtomicLong();

    public Reservation reserve(Player player, LivingEntity target) {
        Reservation reservation = new Reservation(nextAnimationId.incrementAndGet(), player, target);
        if (playerReservations.putIfAbsent(player.getUniqueId(), reservation) != null) {
            return null;
        }
        if (entityReservations.putIfAbsent(target.getUniqueId(), reservation) != null) {
            playerReservations.remove(player.getUniqueId(), reservation);
            return null;
        }
        return reservation;
    }

    public void release(Reservation reservation) {
        if (reservation == null) {
            return;
        }
        playerReservations.remove(reservation.player.getUniqueId(), reservation);
        entityReservations.remove(reservation.target.getUniqueId(), reservation);
    }

    public boolean play(Reservation reservation,
                        double chance,
                        boolean expectedResult,
                        BooleanSupplier targetCompletion,
                        Consumer<Boolean> playerCompletion) {
        if (reservation == null
                || !reservation.started.compareAndSet(false, true)
                || !isCurrent(reservation)) {
            return false;
        }

        LivingEntity target = reservation.target;
        if (!target.isValid() || target.isDead()) {
            release(reservation);
            return false;
        }

        AnimationState state = new AnimationState(
                createFrames(Settings.captureAnimationFrames, expectedResult),
                target.hasAI(),
                target.isInvulnerable(),
                displayName(target),
                formatChance(chance),
                targetCompletion,
                playerCompletion
        );

        try {
            target.setAI(false);
            target.setInvulnerable(true);
        } catch (RuntimeException ex) {
            restoreTarget(reservation, state);
            release(reservation);
            logProcessFailure("Unable to prepare entity for capture animation.", ex);
            return false;
        }

        int intervalTicks = Settings.captureAnimationFrameIntervalTicks;
        ScheduledTask scheduledTask = target.getScheduler().runAtFixedRate(
                Main.plugin,
                task -> tick(reservation, state, task, intervalTicks),
                () -> retire(reservation, state),
                1L,
                intervalTicks
        );
        if (scheduledTask == null) {
            restoreTarget(reservation, state);
            release(reservation);
            return false;
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Capture animation process started: player=" + reservation.player.getName()
                    + ", animation-id=" + reservation.id
                    + ", entity=" + target.getType().name()
                    + ", uuid=" + target.getUniqueId()
                    + ", expected-result=" + (expectedResult ? "success" : "failure")
                    + ", frames=" + state.frames.size() + ", interval-ticks=" + intervalTicks + ".");
        }
        return true;
    }

    private void tick(Reservation reservation, AnimationState state, ScheduledTask task, int intervalTicks) {
        if (!isCurrent(reservation)) {
            task.cancel();
            return;
        }
        if (!reservation.target.isValid() || reservation.target.isDead()) {
            complete(reservation, state, task, false);
            return;
        }

        int index = state.frameIndex.getAndIncrement();
        if (index < state.frames.size()) {
            dispatchProgress(reservation, state.frames.get(index), intervalTicks);
            return;
        }
        complete(reservation, state, task, true);
    }

    private void complete(Reservation reservation, AnimationState state, ScheduledTask task, boolean allowCompletion) {
        if (!state.finished.compareAndSet(false, true)) {
            task.cancel();
            return;
        }

        boolean finalResult = false;
        if (reservation.target.isValid() && !reservation.target.isDead()) {
            restoreTarget(reservation, state);
            if (allowCompletion) {
                try {
                    finalResult = state.targetCompletion.getAsBoolean();
                } catch (RuntimeException ex) {
                    logProcessFailure("Capture completion failed.", ex);
                }
            }
        }

        release(reservation);
        task.cancel();
        dispatchResult(reservation, state, finalResult);
    }

    private void retire(Reservation reservation, AnimationState state) {
        if (!state.finished.compareAndSet(false, true)) {
            return;
        }
        release(reservation);
        dispatchResult(reservation, state, false);
    }

    private void restoreTarget(Reservation reservation, AnimationState state) {
        LivingEntity target = reservation.target;
        if (!target.isValid() || target.isDead()) {
            return;
        }
        try {
            target.setAI(state.originalAi);
            target.setInvulnerable(state.originalInvulnerable);
        } catch (RuntimeException ex) {
            logProcessFailure("Unable to restore entity after capture animation.", ex);
        }
    }

    private void dispatchProgress(Reservation reservation, ProgressFrame frame, int intervalTicks) {
        Player player = reservation.player;
        player.getScheduler().run(Main.plugin, task -> {
            if (!isCurrent(reservation) || !player.isOnline()) {
                return;
            }
            showProgress(player, frame, intervalTicks);
        }, null);
    }

    private void dispatchResult(Reservation reservation, AnimationState state, boolean result) {
        Player player = reservation.player;
        player.getScheduler().run(Main.plugin, task -> {
            try {
                state.playerCompletion.accept(result);
            } catch (RuntimeException ex) {
                logProcessFailure("Capture result callback failed.", ex);
            }

            Reservation newerAnimation = playerReservations.get(player.getUniqueId());
            if (player.isOnline() && (newerAnimation == null || newerAnimation == reservation)) {
                showResult(player, state.entityName, state.formattedChance, result,
                        Settings.captureAnimationResultStayTicks);
            }
        }, null);
    }

    private boolean isCurrent(Reservation reservation) {
        return playerReservations.get(reservation.player.getUniqueId()) == reservation
                && entityReservations.get(reservation.target.getUniqueId()) == reservation;
    }

    private void showProgress(Player player, ProgressFrame frame, int intervalTicks) {
        Title.Times times = Title.Times.times(
                NO_FADE,
                Duration.ofMillis((intervalTicks + 1L) * 50L),
                NO_FADE
        );
        player.showTitle(Title.title(
                Message.component("capture-animation-progress-title"),
                Message.component("capture-animation-progress-subtitle",
                        "success", frame.success(),
                        "failure", frame.failure()),
                times
        ));
    }

    private void showResult(Player player, String entityName, String chance, boolean captured, int stayTicks) {
        String result = captured ? "success" : "failure";
        Title.Times times = Title.Times.times(
                NO_FADE,
                Duration.ofMillis(stayTicks * 50L),
                RESULT_FADE_OUT
        );
        player.showTitle(Title.title(
                Message.component("capture-animation-" + result + "-title",
                        "entity", entityName, "chance", chance),
                Message.component("capture-animation-" + result + "-subtitle",
                        "entity", entityName, "chance", chance),
                times
        ));
    }

    private List<ProgressFrame> createFrames(int frameCount, boolean captured) {
        int losingTarget = ThreadLocalRandom.current().nextInt(55, 91);
        int previousLoser = 0;
        List<ProgressFrame> frames = new ArrayList<>(frameCount);
        for (int index = 1; index <= frameCount; index++) {
            double progress = index / (double) frameCount;
            int winner = index == frameCount ? 100 : Math.min(99, (int) Math.round(progress * 100D));
            int jitter = ThreadLocalRandom.current().nextInt(-2, 3);
            int loser = Math.min(losingTarget, Math.max(previousLoser,
                    (int) Math.round(progress * losingTarget) + jitter));
            previousLoser = loser;
            frames.add(captured
                    ? new ProgressFrame(winner, loser)
                    : new ProgressFrame(loser, winner));
        }
        return frames;
    }

    private String displayName(LivingEntity entity) {
        String[] words = entity.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private String formatChance(double chance) {
        double rounded = Math.round(chance * 10D) / 10D;
        return rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }

    private void logProcessFailure(String message, RuntimeException exception) {
        if (DebugLog.isEnabled()) {
            Main.logger.log(Level.WARNING, message, exception);
        } else {
            Main.logger.warning(message + " " + exception.getMessage());
        }
    }

    public static final class Reservation {
        private final long id;
        private final Player player;
        private final LivingEntity target;
        private final AtomicBoolean started = new AtomicBoolean();

        private Reservation(long id, Player player, LivingEntity target) {
            this.id = id;
            this.player = player;
            this.target = target;
        }
    }

    private static final class AnimationState {
        private final List<ProgressFrame> frames;
        private final boolean originalAi;
        private final boolean originalInvulnerable;
        private final String entityName;
        private final String formattedChance;
        private final BooleanSupplier targetCompletion;
        private final Consumer<Boolean> playerCompletion;
        private final AtomicInteger frameIndex = new AtomicInteger();
        private final AtomicBoolean finished = new AtomicBoolean();

        private AnimationState(List<ProgressFrame> frames,
                               boolean originalAi,
                               boolean originalInvulnerable,
                               String entityName,
                               String formattedChance,
                               BooleanSupplier targetCompletion,
                               Consumer<Boolean> playerCompletion) {
            this.frames = frames;
            this.originalAi = originalAi;
            this.originalInvulnerable = originalInvulnerable;
            this.entityName = entityName;
            this.formattedChance = formattedChance;
            this.targetCompletion = targetCompletion;
            this.playerCompletion = playerCompletion;
        }
    }

    private record ProgressFrame(int success, int failure) {
    }
}
