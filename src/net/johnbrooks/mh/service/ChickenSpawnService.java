package net.johnbrooks.mh.service;

import org.bukkit.Location;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;

import java.util.concurrent.ThreadLocalRandom;

public final class ChickenSpawnService {
    public void spawnEggChickens(Location location) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(8) != 0) {
            return;
        }

        spawnBabyChicken(location);
        if (random.nextInt(32) == 0) {
            spawnBabyChicken(location);
            spawnBabyChicken(location);
            spawnBabyChicken(location);
        }
    }

    private void spawnBabyChicken(Location location) {
        Chicken chicken = (Chicken) location.getWorld().spawnEntity(location, EntityType.CHICKEN);
        chicken.setBaby();
    }
}
