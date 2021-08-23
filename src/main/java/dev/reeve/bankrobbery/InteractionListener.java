package dev.reeve.bankrobbery;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static dev.reeve.bankrobbery.Main.convert;
import static dev.reeve.bankrobbery.Main.robberyPhaseHashMap;

public class InteractionListener implements Listener {
	
	private Main main;
	private Economy economy;
	private final Config config;
	private final ItemStack backgroundItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
	private final ItemStack keypadItem = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
	
	private final List<UUID> players = new ArrayList<>();
	
	public InteractionListener(Main main, Economy economy, Config config) {
		this.main = main;
		this.economy = economy;
		this.config = config;
		
		{
			ItemMeta meta = backgroundItem.getItemMeta();
			meta.setDisplayName(" ");
			
			backgroundItem.setItemMeta(meta);
		}
		
		{
			keypadItem.setAmount((int) config.doorTime);
			
			ItemMeta meta = keypadItem.getItemMeta();
			meta.setDisplayName(convert("&e&l#" + config.doorTime));
			
			keypadItem.setItemMeta(meta);
		}
	}
	
	@EventHandler
	public void onClick(PlayerInteractEvent e) {
		if (e.getClickedBlock() == null) return;
		if (Main.commandUser != null && Main.commandSetting != null && Main.commandUser.getUniqueId().equals(e.getPlayer().getUniqueId())) {
			if (Main.commandSetting == RobberyCommandSetting.KEYPAD) {
				if (!(e.getClickedBlock().getState() instanceof Sign)) return;
				config.keypadLocation = e.getClickedBlock().getLocation();
				e.getPlayer().sendMessage("The keypad has been set.");
				main.saveConfig();
			} else if (Main.commandSetting == RobberyCommandSetting.SIGN) {
				if (!(e.getClickedBlock().getState() instanceof Sign)) return;
				config.robberySignLocation = e.getClickedBlock().getLocation();
				e.getPlayer().sendMessage("The sign has been set.");
				main.saveConfig();
			} else if (Main.commandSetting == RobberyCommandSetting.DOOR) {
				if (e.getClickedBlock().getType() != Material.IRON_DOOR) return;
				config.doorLocation = e.getClickedBlock().getLocation();
				e.getPlayer().sendMessage("The door has been set.");
				main.saveConfig();
			}
			Main.commandUser = null;
			Main.commandSetting = null;
		}
		
		if (!(e.getClickedBlock().getState() instanceof Sign)) return;
		if (config.robberySignLocation == null) return;
		if (config.keypadLocation == null) return;
		if (config.doorLocation == null) return;
		
		Block block = e.getClickedBlock();
		
		if (block.getLocation().equals(config.robberySignLocation)) {
			if (new Date().getTime() - Main.lastRobbery.getTime() < config.robberyCooldown * 1000) {
				long timeLeft = config.robberyCooldown - (new Date().getTime() - Main.lastRobbery.getTime()) / 1000;
				String time = timeLeft / 60 + ":";
				if (timeLeft % 60 < 10) {
					time += "0";
				}
				time += timeLeft % 60;
				e.getPlayer().sendMessage(convert(config.messages.vaultNotReady).replaceAll("%time%", time));
				return;
			}
			
			Bukkit.broadcastMessage(convert(config.messages.bankIsBeingRobbed));
			Main.lastRobbery = new Date();
			
			Player player = e.getPlayer();
			UUID uuid = player.getUniqueId();
			
			robberyPhaseHashMap.put(uuid, RobberyPhase.VAULT);
			new BukkitRunnable() {
				long timer = config.vaultTime;
				
				@Override
				public void run() {
					timer--;
					player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(convert("&aVault: &c" + timer + "s")));
					if (timer == 0) {
						player.sendMessage(convert(config.messages.vaultLooted));
						double amount = (Bukkit.getOnlinePlayers().size() - 1) * config.stolenAmountPerPerson;
						robberyPhaseHashMap.put(uuid, RobberyPhase.ESCAPE);
						
						Main.amountTaken = new HashMap<>();
						
						for (Player player1 : Bukkit.getOnlinePlayers()) {
							if (player1.getUniqueId() != player.getUniqueId())
								if (economy.getBalance(player1) >= config.stolenAmountPerPerson) {
									Main.amountTaken.put(player1.getUniqueId(), config.stolenAmountPerPerson);
									economy.withdrawPlayer(player1, config.stolenAmountPerPerson);
								} else {
									Main.amountTaken.put(player1.getUniqueId(), economy.getBalance(player1));
									economy.withdrawPlayer(player1, Main.amountTaken.get(player1.getUniqueId()));
								}
						}
						
						new BukkitRunnable() {
							long timer = config.survivalTime;
							
							@Override
							public void run() {
								timer--;
								
								if (robberyPhaseHashMap.containsKey(uuid) && robberyPhaseHashMap.get(uuid) == RobberyPhase.ESCAPE) {
									player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(convert("&aEscape: &c" + timer + "s")));
								}
								
								if (timer == 0) {
									cancel();
								}
								
								if (robberyPhaseHashMap.get(uuid) != RobberyPhase.ESCAPE) {
									cancel();
								}
							}
						}.runTaskTimer(Main.instance, 20, 20);
						
						new BukkitRunnable() {
							@Override
							public void run() {
								if (robberyPhaseHashMap.containsKey(uuid) && robberyPhaseHashMap.get(uuid) == RobberyPhase.ESCAPE) {
									robberyPhaseHashMap.remove(uuid);
									economy.depositPlayer(player, amount);
									Bukkit.broadcastMessage(convert(config.messages.bankWasRobbed.replaceAll("%amount%", String.valueOf(amount))));
								}
							}
						}.runTaskLater(Main.instance, config.survivalTime * 20);
						
						cancel();
					}
					
					if (robberyPhaseHashMap.get(uuid) != RobberyPhase.VAULT) {
						cancel();
					}
				}
			}.runTaskTimer(Main.instance, 20, 20);
			
		} else if (block.getLocation().equals(config.keypadLocation)) {
			Inventory keypad = Bukkit.createInventory(null, InventoryType.DISPENSER, "Keypad");
			for (int i = 0; i < 9; i++) {
				if (i != 4)
					keypad.setItem(i, backgroundItem);
				else
					keypad.setItem(i, keypadItem);
			}
			e.getPlayer().openInventory(keypad);
			players.add(e.getPlayer().getUniqueId());
			
			new BukkitRunnable() {
				long timer = config.doorTime;
				
				@Override
				public void run() {
					timer--;
					
					ItemStack item = keypadItem.clone();
					ItemMeta meta = item.getItemMeta();
					meta.setDisplayName(convert("&e&l#" + timer));
					item.setItemMeta(meta);
					item.setAmount((int) timer);
					keypad.setItem(4, item);
					
					if (timer == 0) {
						if (players.contains(e.getPlayer().getUniqueId())) {
							e.getPlayer().closeInventory();
							if (config.doorLocation.getBlock().getState().getBlockData() instanceof Door) {
								BlockState state = config.doorLocation.getBlock().getState();
								Door door = (Door) state.getBlockData();
								door.setOpen(true);
								state.setBlockData(door);
								state.update();
								new BukkitRunnable() {
									@Override
									public void run() {
										door.setOpen(false);
										state.setBlockData(door);
										state.update();
									}
								}.runTaskLater(Main.instance, config.doorTime * 20);
							}
						}
						cancel();
					}
				}
			}.runTaskTimer(Main.instance, 20, 20);
		}
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (e.getView().getTitle().equals("Keypad")) {
			e.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent e) {
		if (e.getView().getTitle().equals("Keypad")) {
			players.remove(e.getPlayer().getUniqueId());
		}
	}
}
