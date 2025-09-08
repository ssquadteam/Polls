package com.ssquadteam.polls.listener;

import com.ssquadteam.polls.PollsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class BookListener implements Listener {

    private final PollsPlugin plugin;

    public BookListener(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType() != Material.WRITTEN_BOOK) {
                    plugin.getSessionManager().markBookClosed(player.getUniqueId());
                }
            }, 5L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSessionManager().markBookClosed(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.WRITTEN_BOOK) {
            plugin.getSessionManager().markBookClosed(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() != Material.WRITTEN_BOOK) {
                plugin.getSessionManager().markBookClosed(player.getUniqueId());
            }
        }, 2L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.WRITTEN_BOOK) {
            plugin.getSessionManager().markBookOpened(player.getUniqueId());
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (currentItem.getType() != Material.WRITTEN_BOOK) {
                    plugin.getSessionManager().markBookClosed(player.getUniqueId());
                }
            }, 2L);
        }
    }
}
