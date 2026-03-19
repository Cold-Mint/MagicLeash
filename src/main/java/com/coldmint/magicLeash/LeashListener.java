package com.coldmint.magicLeash;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class LeashListener implements Listener {

    //From the player who was tied up to the rabbit.
    //从被拴玩家到其兔子。
    private final HashMap<UUID, UUID> leashedPlayerToRabbit = new HashMap<>();
    private final HashMap<UUID, UUID> rabbitToLeashedPlayer = new HashMap<>();
    //The owner goes to the Map where the player is tied up.
    //主人到被拴玩家的Map。
    private final HashMap<UUID, HashSet<UUID>> masterToLeashedPlayerMap = new HashMap<>();
    private final HashMap<UUID, UUID> leashedPlayerToMasterPlayerMap = new HashMap<>();
    //拴绳节到目标玩家。
    private final HashMap<UUID, UUID> leashHitchToLeashedPlayerMap = new HashMap<>();
    //目标玩家到拴绳节。
    private final HashMap<UUID, UUID> leashedPlayerToLeashHitchMap = new HashMap<>();
    private final BukkitTask bukkitTask;

    public LeashListener(JavaPlugin plugin) {
        //Enable the timer, which will trigger once every Tick.
        //启用定时器，每Tick触发一次。
        bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyPhysics, 3L, 3L);
    }

    /**
     * Can a certain player successfully untie the rope knot?
     * 某个玩家是否能解开拴绳节
     *
     * @param player       player 玩家
     * @param leashHitchId leashHitchId 拴绳节
     * @return 如果返回true那么禁止解开。
     */
    private boolean canUnbindLeashKnot(Player player, UUID leashHitchId) {
        //Anyone can use scissors to cut the rope knot.
        //任何人都可用剪刀解开拴绳节
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.SHEARS) {
            useShears(player, item);
            player.sendMessage("你使用剪刀解开了绳子。");
            return false;
        }
        UUID selfId = player.getUniqueId();
        UUID leashedPlayerId = leashHitchToLeashedPlayerMap.get(leashHitchId);
        if (leashedPlayerId == null) {
            //This festival did not involve any players.
            //这个节没有绑玩家。
            return false;
        }
        if (selfId.equals(leashedPlayerId)) {
            UUID masterId = leashedPlayerToMasterPlayerMap.get(leashedPlayerId);
            if (masterId == null) {
                return false;
            }
            Player masterPlayer = Bukkit.getPlayer(masterId);
            if (masterPlayer == null) {
                return false;
            }
            player.sendMessage("你必须使用剪刀解开，或者由" + masterPlayer.getName() + "解开。");
            masterPlayer.sendMessage(player.getName() + "尝试解开绳子。");
            return true;
        }
        UUID masterId = leashedPlayerToMasterPlayerMap.get(leashedPlayerId);
        if (masterId == null) {
            return false;
        }
        Player masterPlayer = Bukkit.getPlayer(masterId);
        if (masterPlayer == null) {
            return false;
        }
        Player leashedPlayer = Bukkit.getPlayer(leashedPlayerId);
        if (leashedPlayer == null) {
            return false;
        }
        if (selfId == masterId) {
            leashedPlayer.sendMessage(masterPlayer.getName() + "牵起了绳子。");
            masterPlayer.sendMessage("你拿起了拴着" + leashedPlayer.getName() + "的绳子。");
            return false;
        }
        player.sendMessage(leashedPlayer.getName() + "被" + masterPlayer.getName() + "拴在了这里，可使用剪刀解开。");
        return true;
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent blockBreakEvent) {
        Player player = blockBreakEvent.getPlayer();
        Block block = blockBreakEvent.getBlock();
        UUID leashHitchId = leashedPlayerToLeashHitchMap.get(player.getUniqueId());
        if (leashHitchId == null) {
            return;
        }
        Entity leashHitchEntity = Bukkit.getEntity(leashHitchId);
        if (leashHitchEntity == null) {
            return;
        }
        if (leashHitchEntity instanceof LeashHitch leashHitch) {
            Location blockLocation = block.getLocation();
            Location leashHitchLocation = leashHitch.getLocation();
            if (blockLocation.getBlockX() == leashHitchLocation.getBlockX() && blockLocation.getBlockY() == leashHitchLocation.getBlockY() && blockLocation.getBlockZ() == leashHitchLocation.getBlockZ()) {
                player.sendMessage("你不能破坏这个方块，可以由其他玩家破坏。");
                blockBreakEvent.setCancelled(true);
            }
        }

    }

    @EventHandler
    public void EntityUnleashEvent(EntityUnleashEvent event) {
        if (!(event.getEntity() instanceof Rabbit rabbit)) {
            return;
        }
        UUID rabbitUUID = rabbit.getUniqueId();
        UUID leashedPlayerId = rabbitToLeashedPlayer.get(rabbitUUID);
        if (leashedPlayerId == null) {
            return;
        }
        UUID masterId = leashedPlayerToMasterPlayerMap.get(leashedPlayerId);
        if (masterId == null) {
            return;
        }
        unbindPlayer(masterId, leashedPlayerId);
        Player master = Bukkit.getPlayer(masterId);
        if (master != null) {
            master.playSound(master.getLocation(), Sound.ITEM_LEAD_TIED, 1, 1);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreakByEntityEvent(HangingBreakByEntityEvent hangingBreakByEntityEvent) {
        Hanging hanging = hangingBreakByEntityEvent.getEntity();
        if (hanging instanceof LeashHitch leashHitch) {
            //Click the left button of the mouse on the interception tethering section.
            //拦截拴绳节的左键点击。
            Entity remover = hangingBreakByEntityEvent.getRemover();
            if (remover instanceof Player self) {
                boolean result = canUnbindLeashKnot(self, leashHitch.getUniqueId());
                hangingBreakByEntityEvent.setCancelled(result);
                if (result) {
                    return;
                }
            }
        }
    }

    /**
     * useShears
     * 使用剪刀
     *
     * @param player 玩家
     * @param shears 剪刀
     */
    private void useShears(Player player, ItemStack shears) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemMeta itemMeta = shears.getItemMeta();
            if (itemMeta instanceof Damageable damageable) {
                int newDamage = damageable.getDamage() + 1;
                if (newDamage >= shears.getType().getMaxDurability()) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    damageable.setDamage(newDamage);
                    shears.setItemMeta(itemMeta);
                }
            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_SHEARS_SNIP, 1, 1);
    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();

        if (event.getHand() != EquipmentSlot.HAND) {
            //If the hand used for player interaction is not the dominant hand.
            //如果玩家互动的手不是主手。
            return;
        }
        if (target instanceof LeashHitch leashHitch) {
            //Right-click on the interception tethering section.
            //拦截拴绳节的右键点击。
            boolean result = canUnbindLeashKnot(event.getPlayer(), leashHitch.getUniqueId());
            event.setCancelled(result);
            if (result) {
                return;
            }
        }
        if (target instanceof Player targetPlayer) {
            Player master = event.getPlayer();
            UUID masterId = master.getUniqueId();
            UUID targetId = targetPlayer.getUniqueId();
            ItemStack item = master.getInventory().getItemInMainHand();
            boolean isPlayersBound = isPlayersBound(masterId, targetId);
            if (item.getType() == Material.SHEARS && isPlayersBound) {
                useShears(master, item);
                unbindPlayer(masterId, targetId);
                event.setCancelled(true);
                return;
            }
            if (item.getType() == Material.LEAD && !isPlayersBound) {
                if (master.getGameMode() != GameMode.CREATIVE) {
                    item.setAmount(item.getAmount() - 1);
                }
                master.playSound(master.getLocation(), Sound.ITEM_LEAD_TIED, 1, 1);
                bindPlayer(master, targetPlayer);
                event.setCancelled(true);
            }
        }

    }

    /**
     * Unbind player
     * 解绑玩家
     *
     * @param masterId masterPlayerId 主人玩家Id
     * @param targetId targetPlayerId 目标玩家Id
     */
    void unbindPlayer(UUID masterId, UUID targetId) {
        Player targetPlayer = Bukkit.getPlayer(targetId);
        if (targetPlayer != null) {
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), ItemStack.of(Material.LEAD));
        }
        Player masterPlayer = Bukkit.getPlayer(masterId);
        if (masterPlayer != null && targetPlayer != null) {
            targetPlayer.sendMessage("你身上的绳子解开了。");
            masterPlayer.sendMessage(targetPlayer.getName() + "身上的绳子解开了。");
        }
        HashSet<UUID> uuids = masterToLeashedPlayerMap.get(masterId);
        if (uuids != null) {
            uuids.remove(targetId);
            if (uuids.isEmpty()) {
                masterToLeashedPlayerMap.remove(masterId);
                leashedPlayerToMasterPlayerMap.remove(targetId);
            }
        }
        UUID rabbitId = leashedPlayerToRabbit.get(targetId);
        if (rabbitId != null) {
            Entity rabbitEntity = Bukkit.getEntity(rabbitId);
            if (rabbitEntity != null) {
                rabbitEntity.remove();
            }
            leashedPlayerToRabbit.remove(targetId);
            rabbitToLeashedPlayer.remove(rabbitId);
        }
        UUID leashHitchId = leashedPlayerToLeashHitchMap.get(targetId);
        if (leashHitchId != null) {
            Entity leashHitchEntity = Bukkit.getEntity(leashHitchId);
            if (leashHitchEntity != null) {
                leashHitchEntity.remove();
            }
            leashHitchToLeashedPlayerMap.remove(leashHitchId);
            leashedPlayerToLeashHitchMap.remove(targetId);
        }
    }

    /**
     * Clear out all the rabbits.
     * 清理所有的兔子。
     */
    public void clearAllRabbit() {
        if (bukkitTask != null && !bukkitTask.isCancelled()) {
            bukkitTask.cancel();
        }
        for (Map.Entry<UUID, UUID> entry : rabbitToLeashedPlayer.entrySet()) {
            Entity rabbitEntity = Bukkit.getEntity(entry.getKey());
            if (!(rabbitEntity instanceof LivingEntity rabbitLivingEntity)) {
                return;
            }
            rabbitLivingEntity.remove();
        }
        leashedPlayerToRabbit.clear();
        rabbitToLeashedPlayer.clear();
        masterToLeashedPlayerMap.clear();
        leashedPlayerToMasterPlayerMap.clear();
        leashHitchToLeashedPlayerMap.clear();
        leashedPlayerToLeashHitchMap.clear();
    }

    void teleportRabbit(Rabbit rabbit, Player targetPlayer) {
        double distance = 0.55;
        Location loc = targetPlayer.getLocation();
        Vector behind = loc.getDirection().multiply(-distance);
        double x = behind.getX();
        double z = behind.getZ();
        Location finalLoc = loc.clone().add(x, 0.9, z);
        rabbit.teleport(finalLoc);
    }

    /**
     * Bind players
     * 绑定玩家
     *
     * @param masterPlayer masterPlayer 主人玩家
     * @param targetPlayer targetPlayer 目标玩家
     */
    void bindPlayer(Player masterPlayer, Player targetPlayer) {
        UUID masterId = masterPlayer.getUniqueId();
        UUID targetId = targetPlayer.getUniqueId();
        if (isTargetPlayerLeashedByAnyPlayer(targetId)) {
            masterPlayer.sendMessage(targetPlayer.getName() + "已被其他玩家拴住。");
            return;
        }

        Location playerLocation = targetPlayer.getLocation();
        //Generate a rabbit at the location of the target player.
        //在目标玩家所在的位置生成一只兔子。
        Rabbit rabbit = targetPlayer.getWorld().spawn(playerLocation, Rabbit.class, r -> {
            r.setAI(false);
            r.setSilent(true);
            r.setGravity(false);
            r.setInvulnerable(true);
            r.setCollidable(false);
            r.setInvisible(true);
            r.setBaby();
            teleportRabbit(r, targetPlayer);
        });

        UUID rabbitId = rabbit.getUniqueId();
        rabbit.setLeashHolder(masterPlayer);

        masterPlayer.sendMessage("你拴住了" + targetPlayer.getName() + "。");
        targetPlayer.sendMessage("你被" + masterPlayer.getName() + "用绳子拴住了。");
        leashedPlayerToRabbit.put(targetId, rabbitId);
        rabbitToLeashedPlayer.put(rabbitId, targetId);

        HashSet<UUID> uuids = masterToLeashedPlayerMap.get(masterId);
        if (uuids == null) {
            uuids = new HashSet<>();
            uuids.add(targetId);
            masterToLeashedPlayerMap.put(masterId, uuids);
        } else {
            uuids.add(targetId);
        }

        leashedPlayerToMasterPlayerMap.put(targetId, masterId);
    }

    /**
     * Did the host tied the target player with a leash?
     * 主人是否用拴绳绑定了目标玩家。
     *
     * @param masterPlayerId masterPlayerId 主人的玩家ID
     * @param targetPlayerId targetPlayerId 目标的玩家ID
     * @return Is it bound 是否绑定
     */
    private boolean isPlayersBound(UUID masterPlayerId, UUID targetPlayerId) {
        HashSet<UUID> uuids = masterToLeashedPlayerMap.get(masterPlayerId);
        return uuids != null && uuids.contains(targetPlayerId);
    }

    /**
     * Is the target player bound to any particular player?
     * 目标玩家是否被任意玩家绑定。
     *
     * @param targetPlayerId targetPlayerId 目标玩家
     * @return Is it bound? 是否绑定。
     */
    private boolean isTargetPlayerLeashedByAnyPlayer(UUID targetPlayerId) {
        return leashedPlayerToRabbit.containsKey(targetPlayerId);
    }

    private void applyPhysics() {
        for (Map.Entry<UUID, UUID> entry : rabbitToLeashedPlayer.entrySet()) {
            applyHookeLawPhysics(entry.getKey(), entry.getValue());
        }
    }

    private void applyHookeLawPhysics(UUID rabbitId, UUID targetPlayerId) {
        Entity targetEntity = Bukkit.getEntity(targetPlayerId);
        if (!(targetEntity instanceof Player targetPlayer)) {
            return;
        }
        Entity rabbitEntity = Bukkit.getEntity(rabbitId);
        if (!(rabbitEntity instanceof Rabbit rabbitLivingEntity)) {
            return;
        }
        if (!rabbitLivingEntity.isLeashed()) {
            return;
        }

        Location targetPlayerLocation = targetPlayer.getLocation();
        // 让兔子始终保持在targetPlayer的碰撞箱内（跟随targetPlayer同步运动）
        // 计算targetPlayer的碰撞箱中心位置（大约在玩家脚部上方1.2格）
        // 使用targetPlayer的精确位置，确保兔子完全在玩家内部
        teleportRabbit(rabbitLivingEntity, targetPlayer);
        Entity leashHolder = rabbitLivingEntity.getLeashHolder();
        if (leashHolder instanceof LeashHitch leashHitch) {
            UUID leashHitchId = leashHitch.getUniqueId();
            leashHitchToLeashedPlayerMap.put(leashHitchId, targetPlayerId);
            leashedPlayerToLeashHitchMap.put(targetPlayerId, leashHitchId);
        } else {
            UUID leashHitchId = leashedPlayerToLeashHitchMap.get(targetPlayerId);
            if (leashHitchId != null) {
                leashedPlayerToLeashHitchMap.remove(targetPlayerId);
                leashHitchToLeashedPlayerMap.remove(leashHitchId);
            }
        }
        // 计算兔子和拴绳持有者的距离（拴绳长度）
        Location leashHolderLoc = leashHolder.getLocation();
        double distance = rabbitLivingEntity.getLocation().distance(leashHolderLoc);
        org.bukkit.util.Vector dir = leashHolderLoc.toVector().subtract(targetPlayerLocation.toVector());
        if (distance > 6) {
            dir.normalize();
            Vector velocity = dir.multiply(0.35);
            targetPlayer.setVelocity(
                    targetPlayer.getVelocity().add(velocity)
            );
        }
    }

    /**
     * Bidirectional unbinding.
     * 双向解绑。
     *
     * @param targetPlayerId 目标玩家Id
     */
    void unbindAllRelations(UUID targetPlayerId) {
        List<UUID[]> unbindPairs = new ArrayList<>();
        for (Map.Entry<UUID, HashSet<UUID>> entry : new HashMap<>(masterToLeashedPlayerMap).entrySet()) {
            UUID masterId = entry.getKey();
            HashSet<UUID> targetIds = new HashSet<>(entry.getValue());
            if (masterId.equals(targetPlayerId)) {
                for (UUID targetId : targetIds) {
                    unbindPairs.add(new UUID[]{masterId, targetId});
                }
            } else if (targetIds.contains(targetPlayerId)) {
                unbindPairs.add(new UUID[]{masterId, targetPlayerId});
            }
        }
        for (UUID[] pair : unbindPairs) {
            unbindPlayer(pair[0], pair[1]);
        }
    }

    /**
     * 玩家死亡事件处理
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        unbindAllRelations(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家退出游戏事件处理
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unbindAllRelations(event.getPlayer().getUniqueId());
    }
}